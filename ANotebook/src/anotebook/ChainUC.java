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
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;

/**
 *
 * @author Сергей
 */
public class ChainUC extends TitledPane {
    @FXML private ListView<String> listViewMain;
    public ChainUC()
    {
        this("0");
    }
    public ChainUC(String title)
    {
        this(title, FXCollections.observableArrayList());
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
        {
            PreparedStatement st = ANotebook.m_conn.prepareStatement("insert into words2chain(wordId, chainId) values(?, ?)");
            st.setLong(1, wordId);
            st.setLong(2, m_DBId);
            st.execute();
        }
        words.add(word);
    }
    public Long m_DBId;
    public void CreateChain() throws SQLException
    {
        PreparedStatement st = ANotebook.m_conn.prepareStatement("insert into chains(timestamp, timestampSelected) values(?, ?)");
        Timestamp ts = ANotebook.getCurrentTimestamp();
        st.setTimestamp(1, ts);
        st.setTimestamp(2, ts);
        st.execute();
//        ResultSet rs = ANotebook.m_conn.createStatement().executeQuery("select IDENTITY()");
        ResultSet rs = ANotebook.m_conn.createStatement().executeQuery("values IDENTITY_VAL_LOCAL()");
        if (rs.next())
            m_DBId = rs.getLong(1);
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
                "select word from words "
                        + "inner join words2chain on words.id = words2chain.wordId "
                        + "where chainId = ?");
        st.setLong(1, m_DBId);
        ResultSet rs = st.executeQuery();
        while (rs.next())
            words.add(rs.getString("word"));
    }
    ObservableList<String> words;
    public ChainUC(String title, ObservableList<String> words) {
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
        words.addListener(new ListChangeListener<String>() {

            @Override
            public void onChanged(ListChangeListener.Change<? extends String> c) {
                listViewMain.scrollTo(words.size() - 1);
            }
        });
    } 
}
