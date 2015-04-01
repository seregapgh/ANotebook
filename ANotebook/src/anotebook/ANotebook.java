/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package anotebook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 *
 * @author Сергей
 */
public class ANotebook extends Application {
    class InputChangeEventHandler implements ChangeListener<String>
    {
        private ANotebook m_anb;
        public InputChangeEventHandler(ANotebook anb)
        {
            m_anb = anb;
        }

        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String text) {
            if (text.length() > 4 && text.charAt(0) == '?' && text.charAt(text.length()-1) == ' ')
            {
                try {
                    String q = text.substring(2);
                    String[] qs = q.split(" ");
                    StringBuilder condition = new StringBuilder();
                    for (int i = 0; i < qs.length; i ++) {
                        if (i > 0)
                            condition.append(" OR ");
                        condition.append("word LIKE ?");
                    }
                    PreparedStatement ps = ANotebook.m_conn.prepareStatement("SELECT chains.externalChainId "
                            + "FROM words inner join words2chain on words.id = words2chain.wordId "
                            + "     inner join chains on words2chain.chainId = chains.id "
                            + "WHERE " + condition.toString() +" "
                            + "GROUP BY externalChainId "
                            + "ORDER BY COUNT(*) DESC, MAX(chains.timestampSelected) DESC "
                            //+ "LIMIT 20"
                            + "FETCH FIRST 20 ROWS ONLY"
                    );
                    for (int i = 0; i < qs.length; i ++) {
                        ps.setString(i+1, qs[i]);
                    }
                    ResultSet rs = ps.executeQuery();
                    if (!m_anb.m_chucs.containsKey(ViewType.SEARCH))
                    {
                        m_anb.m_gp.put(ViewType.SEARCH, new GridPane());
                        m_anb.m_chucs.put(ViewType.SEARCH, new ArrayList<>());
                    }
                    //List<ChainUC> chucs = m_anb.m_chucs.get(ViewType.SEARCH);
                    m_anb.m_gp.get(ViewType.SEARCH).getChildren().clear();
                    m_anb.m_chucs.get(ViewType.SEARCH).clear();
                    Stack<ChainUC> chucs = new Stack<>();
                    while (rs.next())
                    {
                        ChainUC chuc = new ChainUC();
                        chuc.m_DBId = rs.getLong("externalChainId");
                        chuc.Load();
                        chucs.push(chuc);

                    }
                    while (!chucs.empty())
                        addChain(chucs.pop(), ViewType.SEARCH);
                    m_anb.m_sp.setContent(m_anb.m_gp.get(ViewType.SEARCH));

                } catch (SQLException ex) {
                    Logger.getLogger(ANotebook.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }
        
    }
    class InputActionEventHandler implements EventHandler<ActionEvent>
    {
        private ANotebook m_anb;
        public InputActionEventHandler(ANotebook anb)
        {
            m_anb = anb;
        }
        @Override
        public void handle(ActionEvent event) {
            String word = m_anb.m_input.getText();
            if (!word.isEmpty() && word.charAt(0) == '?')
            {
                if(word.contains("\\"))
                {
                    int startPos = word.indexOf("\\");
                    int id = Integer.parseUnsignedInt(word.substring(startPos+1));
                    if (id < m_anb.m_chucs.get(ViewType.SEARCH).size())
                    {
                        ChainUC chuc = m_anb.m_chucs.get(ViewType.SEARCH).get(id);
                        try {
                            m_anb.addChain2Main(chuc);
                        } catch (SQLException ex) {
                            Logger.getLogger(ANotebook.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    m_anb.m_input.setText(word.substring(0, startPos));
                    m_anb.m_input.positionCaret(startPos);
                }
                else
                {
                    m_anb.m_sp.setContent(m_anb.m_gp.get(ViewType.MAIN));
                    m_anb.m_input.setText("");
                }
                    
            }
            else if(word.contains("\\"))
            {
                try
                {
                    int startPos = word.indexOf("\\");
                    int id = Integer.parseUnsignedInt(word.substring(startPos+1));
                    if (id < m_anb.m_chucs.get(ViewType.MAIN).size())
                    {
                        ChainUC chuc = m_anb.m_chucs.get(ViewType.MAIN).get(id);
                        chuc.Activate();
                        m_anb.m_chucs.get(ViewType.MAIN).remove(id);
                        m_anb.m_gp.get(ViewType.MAIN).getChildren().remove(chuc);
                        for(int i = id - 1; i >= 0; i--)
                        {
                            GridPane.setConstraints(m_anb.m_chucs.get(ViewType.MAIN).get(i), i + 1, 0);
                            m_anb.m_chucs.get(ViewType.MAIN).get(i).setText(Integer.toString(i+1));
                        }
                        chuc.setText("0");
                        m_anb.m_chucs.get(ViewType.MAIN).add(0, chuc);
                        m_anb.m_gp.get(ViewType.MAIN).add(chuc, 0, 0);
                        m_anb.m_sp.setHvalue(m_anb.m_sp.getHmin());
                        m_anb.m_input.setText("");
                        m_anb.setCurrentSelection(0);
                    }
                }
                catch (NumberFormatException | SQLException ex)
                {
                }
            } 
            else if (!word.isEmpty())
            {
                try {
                    m_anb.getCurrentChainUC().AddWord(word);
                    m_anb.m_input.setText("");
                } catch (SQLException ex) {
                    Logger.getLogger(ANotebook.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else
            {
                try {
                    ChainUC chuc = new ChainUC();
                    chuc.CreateChain();
                    m_anb.addChain(chuc, ViewType.MAIN);
                    m_anb.setCurrentSelection(0);
                } catch (SQLException ex) {
                    Logger.getLogger(ANotebook.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    class InputKeyEventHandler implements EventHandler<KeyEvent>
    {
        private ANotebook m_anb;
        public InputKeyEventHandler(ANotebook anb)
        {
            m_anb = anb;
        }
        @Override
        public void handle(KeyEvent keyEvent) {
            if (keyEvent.isControlDown())
            {
                if (keyEvent.getCode() == KeyCode.RIGHT)
                    m_anb.setRightSelection();
                if (keyEvent.getCode() == KeyCode.LEFT)
                    m_anb.setLeftSelection();
            }
            else if (keyEvent.getCode() == KeyCode.UP
                    || keyEvent.getCode() == KeyCode.DOWN)
            {
                m_anb.getCurrentChainUC().fireEvent(keyEvent);
                keyEvent.consume();
            }
        }
        
    }
    
    private int m_currentSelection;
    private ChainUC m_currentSelectionChainUC;
    public int getCurrentSelection()
    {
        return m_currentSelection;
    }
    public ChainUC getCurrentChainUC()
    {
        return m_currentSelectionChainUC;
    }
    public void setCurrentSelection(int selection)
    {
        if (m_currentSelectionChainUC != null)
            m_currentSelectionChainUC.lookup(".title").setStyle("-fx-background-color: lightgrey;");;
        if (selection < m_chucs.get(ViewType.MAIN).size())
            m_currentSelection = selection;
        else
            m_currentSelection = 0;
        m_currentSelectionChainUC = m_chucs.get(ViewType.MAIN).get(m_currentSelection);
        Node n = m_currentSelectionChainUC.lookup(".title");
        if (n != null)
            n.setStyle("-fx-background-color: lightgreen;");
        double width = m_sp.getContent().getBoundsInLocal().getWidth() - m_sp.getWidth(); 
        double viewBegin = m_sp.getHvalue() * width;
        double newBegin = m_currentSelectionChainUC.getBoundsInParent().getMinX();
        double newEnd = m_currentSelectionChainUC.getBoundsInParent().getMaxX();
        if (viewBegin > newBegin)
            m_sp.setHvalue(newBegin / width);
        else if (newEnd > viewBegin + m_sp.getWidth())
            m_sp.setHvalue((newEnd - m_sp.getWidth()) / width);
    }
    public void setRightSelection()
    {
        setCurrentSelection(m_currentSelection +1);
    }
    public void setLeftSelection()
    {
        if (m_currentSelection > 0)
            setCurrentSelection(m_currentSelection - 1);
        else
            setCurrentSelection(m_chucs.get(ViewType.MAIN).size() - 1);
    }
    BorderPane m_root;
    public TextField m_input;
    public enum ViewType
    {
        MAIN,
        SEARCH,
    }
    public EnumMap<ViewType, List<ChainUC>> m_chucs;
    public EnumMap<ViewType, GridPane> m_gp;
    public ScrollPane m_sp;
    public Stage m_stage;
    public static Connection m_conn;
    public void addChain2Main(ChainUC chuc) throws SQLException
    {
        long chucId = chuc.m_DBId;
        List<ChainUC> mainChucs = m_chucs.get(ViewType.MAIN);
        ChainUC chucInMain = null;
        int id = 0;
        for(; id < mainChucs.size(); id++)
        {
            if (mainChucs.get(id).m_DBId == chucId)
            {
                chucInMain = mainChucs.get(id);
                break;
            }
        }
        if (chucInMain != null)
        {
            chucInMain.Activate();
            mainChucs.remove(id);
            GridPane mainGP = m_gp.get(ViewType.MAIN);
            mainGP.getChildren().remove(chuc);
            for(int i = id - 1; i >= 0; i--)
            {
                GridPane.setConstraints(mainChucs.get(i), i + 1, 0);
                mainChucs.get(i).setText(Integer.toString(i+1));
            }
            chuc.setText("0");
            mainChucs.add(0, chuc);
            mainGP.add(chuc, 0, 0);
        }
        else
        {
            ChainUC newChuc = new ChainUC();
            newChuc.m_DBId = chucId;
            newChuc.Load();
            addChain(newChuc, ViewType.MAIN);
        }
    }
    public  void addChain(ChainUC chuc, ViewType vt)
    {
        for (int i = m_chucs.get(vt).size() - 1; i >= 0; i--)
        {
            m_chucs.get(vt).get(i).setText(Integer.toString(i+1));
            GridPane.setConstraints(m_chucs.get(vt).get(i), i+1, 0);
        }
        m_chucs.get(vt).add(0, chuc);
        m_gp.get(vt).add(m_chucs.get(vt).get(0), 0, 0);
    }
    private void loadTopChains(int number) throws SQLException
    {
        PreparedStatement st = m_conn.prepareStatement(
//                "select top ? id from chains order by timestampSelected desc");
                "select id from chains where externalChainId = id order by timestampSelected desc FETCH FIRST ? ROWS ONLY");
        
        st.setInt(1, number);
        ResultSet rs = st.executeQuery();
        Stack<ChainUC> chucs = new Stack<>();
        while (rs.next())
        {
            ChainUC chuc = new ChainUC();
            chuc.m_DBId = rs.getLong("id");
            chuc.Load();
            chucs.push(chuc);

        }
        while (!chucs.empty())
            addChain(chucs.pop(), ViewType.MAIN);
    }
    private void initializeDB() throws Exception
    {
        try
        {
//            m_ds = new JdbcDataSource();
//            m_ds.setURL("jdbc:h2:DB/current");
//            m_ds.setUser("sa");
//            m_ds.setPassword("sa");
//            m_conn = m_ds.getConnection();
            String dbURL = "jdbc:derby:DDB/work;create=true";
            m_conn = DriverManager.getConnection(dbURL);
            Statement s = m_conn.createStatement();
//            s.execute("create table if not exists Chains(id BIGINT AUTO_INCREMENT PRIMARY KEY, timestamp TIMESTAMP, timestampSelected TIMESTAMP);");
//            s.execute("create table if not exists Words(id BIGINT AUTO_INCREMENT PRIMARY KEY, "
//                    + "word VARCHAR(100), timestamp TIMESTAMP)");
//            s.execute("create table if not exists Words2Chain("
//                    + "wordId BIGINT,"
//                    + "chainId BIGINT, "
//                    + "PRIMARY KEY (wordId, chainId), "
//                    + "FOREIGN KEY(wordId) REFERENCES Words(id), "
//                    + "FOREIGN KEY(chainId) REFERENCES Chains(id))");
            s.execute("create table Chains(id BIGINT generated by default as identity PRIMARY KEY, timestamp TIMESTAMP, timestampSelected TIMESTAMP)");
            s.execute("create table Words(id BIGINT generated by default as identity PRIMARY KEY, "
                    + "word VARCHAR(100), timestamp TIMESTAMP)");
            s.execute("create table Words2Chain("
                    + "wordId BIGINT,"
                    + "chainId BIGINT, "
                    + "PRIMARY KEY (wordId, chainId), "
                    + "FOREIGN KEY(wordId) REFERENCES Words(id), "
                    + "FOREIGN KEY(chainId) REFERENCES Chains(id))");
        }
        catch (SQLException ex)
        {
            if(!ex.getSQLState().equalsIgnoreCase("X0Y32") )
                throw ex;
        }
        try
        {
            Statement s = m_conn.createStatement();
            s.execute("alter table Chains add column externalChainId BIGINT");
            s.execute("alter table Chains add column nextWordId BIGINT");
            s.execute("alter table Chains add FOREIGN KEY(externalChainId) REFERENCES Chains(id)");
            s.execute("update Chains set externalChainId = id");
            s.execute("alter table Chains add FOREIGN KEY(nextWordId) REFERENCES Words(id)");
        }
        catch (SQLException ex)
        {
            if(!ex.getSQLState().equalsIgnoreCase("X0Y32") )
                throw ex;
        }
        
    }
    public static Timestamp getCurrentTimestamp()
    {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.util.Date utilDate = cal.getTime();
        Timestamp ts = new Timestamp(utilDate.getTime());
        return ts;
    }
    @Override
    public void init() throws java.lang.Exception
    {
        m_gp = new EnumMap<>(ViewType.class);
        m_chucs = new EnumMap<>(ViewType.class);
        initializeDB();
        m_gp.put(ViewType.MAIN, new GridPane());
        m_chucs.put(ViewType.MAIN, new ArrayList<>());
        loadTopChains(20);
        if (m_chucs.get(ViewType.MAIN).isEmpty())
        {
            ChainUC chuc = new ChainUC();
            chuc.CreateChain();
            addChain(chuc, ViewType.MAIN);
        }
        m_currentSelectionChainUC = m_chucs.get(ViewType.MAIN).get(0);
            
        m_input = new TextField();
        m_input.textProperty().addListener(new InputChangeEventHandler(this));
        //m_input.setOnKeyTyped(new InputKeyEventHandler(this));
        m_input.setOnAction(new InputActionEventHandler(this));
        m_input.setOnKeyPressed(new InputKeyEventHandler(this));
        m_root = new BorderPane();
        m_root.setBottom(m_input);
        m_sp = new ScrollPane(m_gp.get(ViewType.MAIN));
        m_sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        m_sp.setFitToHeight(true);
        m_root.setCenter(m_sp);
    }
    @Override
    public void start(Stage stage) throws Exception {
        m_stage = stage;
        stage.setTitle("ANotebook");
        

        Preferences userPrefs = Preferences.userNodeForPackage(getClass());
        // get window location from user preferences: use x=100, y=100, width=400, height=400 as default
        double x = userPrefs.getDouble("stage.x", 100);
        double y = userPrefs.getDouble("stage.y", 100);
        double w = userPrefs.getDouble("stage.width", 800);
        double h = userPrefs.getDouble("stage.height", 450);
        Rectangle2D bounds = Screen.getPrimary().getBounds();
        if( h > 9 * bounds.getHeight() /10)
            h = 9 * bounds.getHeight() /10;
        

        Scene scene = new Scene(m_root);
        stage.setScene(scene);
        stage.setX(x);
        stage.setY(y);
        stage.setWidth(w);
        stage.setHeight(h);        
        stage.setScene(scene);
        stage.show();
    }
     @Override
    public void stop() 
    {
        if(!m_stage.isMaximized() && !m_stage.isFullScreen() && !m_stage.isIconified())
        {
            Preferences userPrefs = Preferences.userNodeForPackage(getClass());
            userPrefs.putDouble("stage.x", m_stage.getX());
            userPrefs.putDouble("stage.y", m_stage.getY());
            userPrefs.putDouble("stage.width", m_stage.getWidth());
            userPrefs.putDouble("stage.height", m_stage.getHeight());
        }
        try {
            m_conn.close();
            DriverManager.getConnection(
                "jdbc:derby:;shutdown=true");
        } catch (SQLException ex) {
            if (!ex.getSQLState().equals("XJ015"))
                Logger.getLogger(ANotebook.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
    
}
