/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package anotebook;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 *
 * @author Сергей
 */
public class ChainUC extends TitledPane {
    public class Word
    {
        public Word(String word, long id)
        {
            m_word = word;
            m_id = id;
        }
        public long getId()
        {
            return m_id;
        }
        private String m_word;
        private long m_id;
        @Override public String toString()
        {
            return m_word;
        }
    }
    @FXML private ListView<Word> listViewMain;
    public ChainUC()
    {
        this("0");
    }
    public ChainUC(String title)
    {
        this(title, FXCollections.observableArrayList());
        this.setOnKeyPressed(new InputKeyEventHandler(this));
    }
    class InputKeyEventHandler implements EventHandler<KeyEvent>
    {
        private ChainUC m_chuc;
        public InputKeyEventHandler(ChainUC chuc)
        {
            m_chuc = chuc;
        }
        @Override
        public void handle(KeyEvent keyEvent) {
            ListView<Word> lvm = m_chuc.listViewMain;
            int si = lvm.getSelectionModel().getSelectedIndex();
            if (keyEvent.getCode() == KeyCode.UP)
            {
                if (si > 0)
                    lvm.fireEvent(keyEvent);
                else if (si == -1)
                    lvm.getSelectionModel().selectLast();
                else
                    lvm.getSelectionModel().clearSelection();
            }
            if (keyEvent.getCode() == KeyCode.DOWN)
            {
                if (si < words.size() - 1)
                    m_chuc.listViewMain.fireEvent(keyEvent);
                else
                    lvm.getSelectionModel().clearSelection();
            }
        }
        
    }
    public void AddWord(String word) throws SQLException
    {
        Activate();
        long wordId;
        {
            PreparedStatement st = ANotebook.m_conn.prepareStatement("insert into words(word, timestamp) values(?,?)");
            st.setString(1, word);
            st.setTimestamp(2, ANotebook.getCurrentTimestamp());
            st.execute();
//            ResultSet rs = ANotebook.m_conn.createStatement().executeQuery("select IDENTITY()");
            ResultSet rs = ANotebook.m_conn.createStatement().executeQuery("values IDENTITY_VAL_LOCAL()");
            if (rs.next())
                wordId = rs.getLong(1);
            else
                throw new SQLException("Can't get word id");
        }
        Word selectedWord = listViewMain.getSelectionModel().getSelectedItem();
        int selectedWordIndex = listViewMain.getSelectionModel().getSelectedIndex();
        PreparedStatement stInsertW2C = ANotebook.m_conn.prepareStatement("insert into words2chain(wordId, chainId) values(?, ?)");
        stInsertW2C.setLong(1, wordId);
        if (selectedWord == null)
        {
            stInsertW2C.setLong(2, m_DBId);
            stInsertW2C.execute();
            words.add(new Word(word, wordId));
        }
        else
        {
            PreparedStatement st = ANotebook.m_conn.prepareStatement("select id from chains where nextWordId = ?");
            st.setLong(1, selectedWord.getId());
            ResultSet rs = st.executeQuery();
            if (rs.next())
            {
                long chainId = rs.getLong("id");
                stInsertW2C.setLong(2, chainId);
                stInsertW2C.execute();
                words.add(selectedWordIndex, new Word(word, wordId));
            }
            else
            {
                long chainId = CreateChainExtended(m_DBId, selectedWord.getId());
                stInsertW2C.setLong(2, chainId);
                stInsertW2C.execute();
                words.add(selectedWordIndex, new Word(word, wordId));
            }
        }
        
    }
    public Long m_DBId;
    public long CreateChainExtended(Long externalChainId, Long nextWordId) throws SQLException
    {
        PreparedStatement st = ANotebook.m_conn.prepareStatement("insert into chains(timestamp, timestampSelected) values(?, ?)");
        Timestamp ts = ANotebook.getCurrentTimestamp();
        st.setTimestamp(1, ts);
        st.setTimestamp(2, ts);
        st.execute();
//        ResultSet rs = ANotebook.m_conn.createStatement().executeQuery("select IDENTITY()");
        ResultSet rs = ANotebook.m_conn.createStatement().executeQuery("values IDENTITY_VAL_LOCAL()");
        long id;
        if (rs.next())
            id = rs.getLong(1);
        else
            throw new SQLException("Can't get chain id");
        st = ANotebook.m_conn.prepareStatement("update chains set externalChainId = ? where id = ?");
        if (externalChainId == null)
            st.setLong(1, id);
        else
            st.setLong(1, externalChainId);
        st.setLong(2, id);
        st.execute();
        if (nextWordId != null)
        {
            st = ANotebook.m_conn.prepareStatement("update chains set nextWordId = ? where id = ?");
            st.setLong(1, nextWordId);
            st.setLong(2, id);
            st.execute();
        }
        return id;
    }
    public void CreateChain() throws SQLException
    {
        m_DBId = CreateChainExtended(null, null);
    }
    public void Activate() throws SQLException
    {
        PreparedStatement st = ANotebook.m_conn.prepareStatement("update chains set timestampSelected = ? where id = ?");
        st.setTimestamp(1, ANotebook.getCurrentTimestamp());
        st.setLong(2, m_DBId);
        st.execute();
    }
    public void Load() throws SQLException
    {
        words.clear();
        PreparedStatement st = ANotebook.m_conn.prepareStatement(
                "select word, wordId, nextWordId from words "
                        + "inner join words2chain on words.id = words2chain.wordId "
                        + "inner join chains on words2chain.chainId = chains.id "
                        + "where externalChainId = ?"
                        + "order by chains.id, words.id");
        st.setLong(1, m_DBId);
        ResultSet rs = st.executeQuery();
        while (rs.next())
        {
            Long nextWordId = rs.getLong("nextWordId");
            if (rs.wasNull())
                nextWordId = null;
            Word word = new Word(rs.getString("word"), rs.getLong("wordId"));
            if (nextWordId == null)
                words.add(word);
            else
            {
                int index = 0;
                for (Word w: words)
                    if (w.getId() != nextWordId)
                        index++;
                    else
                        break;
                words.add(index, word);
            }
        }
    }
    ObservableList<Word> words;
    public ChainUC(String title, ObservableList<Word> words) {
        this.words = words;
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(
"ChainUC.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        super.setText(title);
        listViewMain.setItems(words);
        words.addListener(new ListChangeListener<Word>() {

            @Override
            public void onChanged(ListChangeListener.Change<? extends Word> c) {
                listViewMain.scrollTo(words.size() - 1);
            }
        });
    } 
}
