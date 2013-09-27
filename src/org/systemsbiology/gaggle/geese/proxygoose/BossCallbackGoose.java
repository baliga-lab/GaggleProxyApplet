package org.systemsbiology.gaggle.geese.proxygoose;

import com.sun.java.browser.dom.DOMService;
import netscape.javascript.JSObject;
import org.systemsbiology.gaggle.core.Boss;
import org.systemsbiology.gaggle.core.Boss3;
import org.systemsbiology.gaggle.core.Goose3;
import org.systemsbiology.gaggle.core.datatypes.*;
import org.systemsbiology.gaggle.geese.common.GaggleConnectionListener;
import org.systemsbiology.gaggle.geese.common.RmiGaggleConnector;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Created with IntelliJ IDEA.
 * User: Ning Jiang
 * Date: 8/9/13
 * Time: 7:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class BossCallbackGoose extends UnicastRemoteObject implements Goose3, GaggleConnectionListener {
    private GaggleProxyApplet applet = null;
    private JSObject browser = null;
    final static String defaultGooseName = "ProxyAppletCallbackGoose";
    String gooseName = defaultGooseName;
    private String uri = "rmi://localhost/gaggle";
    Boss3 boss = null;
    RmiGaggleConnector connector = new RmiGaggleConnector(this);
    String activeGooseNames[] = new String[0];

    String species = "unknown";
    String[] nameList;
    String size;
    String type = null;
    Tuple metadata;
    DOMService service = null;
    Object workflowSyncObj = null;

    public BossCallbackGoose(GaggleProxyApplet myApplet, JSObject browser, Object workflowSyncObj) throws RemoteException
    {
        this.applet = myApplet;
        this.browser = browser;
        this.workflowSyncObj = workflowSyncObj;

        try
        {
            this.service = DOMService.getService(myApplet);
        }
        catch (Exception e)
        {
            System.out.println("Error starting RMI connector: " + e.getMessage());
        }
    }

    public GaggleGooseInfo getGooseInfo()
    {
        return null;
    }


    public void handleWorkflowAction(WorkflowAction action)
    {

    }

    public void saveState(String directory, String filePrefix) throws RemoteException
    {

    }

    public void loadState(String location) throws RemoteException
    {

    }

    public void setConnected(boolean connected, Boss boss) {
        if (connected) {
            if (boss.getClass().isInstance(Boss3.class))  // if the boss is an earlier version, we ignore it.
                this.boss = (Boss3)boss;
            else
                this.boss = null;
        }
        else {
            System.out.println("Disconnected from boss");
            // here we need to call setBoss to synchronize the setting
            this.boss = null;
        }

        if (this.boss != null)
        {
            System.out.println("set connected: " + connected);
            System.out.println("isConnected: " + connector.isConnected());
        }
    }

    public boolean isConnected() {
        return connector.isConnected();
    }

    public void handleWorkflowInformation(final String type, final String info)
    {
        if (browser == null || applet == null || !applet.isRunning())
            return;

        synchronized (workflowSyncObj)
        {
            try
            {
                if (type.equalsIgnoreCase("Information"))
                {
                    if (info.equalsIgnoreCase("Workflow Finished"))
                    {
                        System.out.println("Proxy got workflow finish notification");
                        browser.call("OnWorkflowFinished", null);
                        System.out.println("workflow finished");
                    }
                    else
                        browser.call("DisplayInfo", new String[] {"#divLogInfo", info, "info"});
                }
                else if (type.equalsIgnoreCase("Error"))
                {
                    browser.call("DisplayInfo", new String[] {"#divLogInfo", info, "error"});
                }
                else if (type.equalsIgnoreCase("Warning"))
                {
                    browser.call("DisplayInfo", new String[] {"#divLogInfo", info, "warning"});
                }
                else if (type.equalsIgnoreCase("Recording")) {
                    System.out.println("Received broadcast recording: " + info);
                    //Object[] params = info.split(";");
                    browser.call("UpdateRecordingInfo", new Object[]{info});
                }
                else if (type.equalsIgnoreCase("SaveStateResponse"))
                {
                    try
                    {
                        System.out.println("Received save state response: " + info);
                        //JSONObject jsonObject = JSONObject.fromObject(info);
                        //JSONObject stateObj = (JSONObject)jsonObject.get("state");
                        //String id = stateObj.getString("id");
                        //String name = stateObj.getString("name");
                        //String desc = stateObj.getString("desc");
                        //System.out.println("ID " + id + " name " + name + " desc " + desc);
                        //Object[] params = info.split(";");
                        browser.call("OnSaveState", new String[]{info}); // {(id + ";;" + name + ";;" + desc)});
                    }
                    catch (Exception e0)
                    {
                        System.out.println("Failed to call back OnSaveState " + e0.getMessage());
                        e0.printStackTrace();
                    }
                }
                else if (type.equalsIgnoreCase("WorkflowInformation"))
                {
                    System.out.println("Passing workflow ID " + info + " to proxy applet");
                    browser.call("SetWorkflowID", new String[]{info});
                }
                else if (type.equalsIgnoreCase("UploadResponse"))
                {
                    System.out.println("Process upload response " + info);
                    browser.call("ProcessBossUploadResult", new String[]{info});
                }
            }
            catch (Exception e1)
            {
                System.out.println("Failed to execute javascript function " + e1.getMessage());
                e1.printStackTrace();
            }
        }
    }

    public void handleTable(String source, Table table)
    {

    }

    public void connectToBoss(boolean force)
    {
        if (this.boss == null || force)
        {
            boss = null;
            System.out.println("Callback goose connecting to boss...");
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try
                    {
                        int check = 0;
                        connectToGaggle();
                        while (boss == null && check < 9)
                        {
                            Thread.sleep(5000);
                            try {
                                boss = (org.systemsbiology.gaggle.core.Boss3)connector.getBoss();
                            }
                            catch (Exception eb)
                            {
                                System.out.println("Failed to get boss " + eb.getMessage());
                                boss = null;
                            }
                            //System.out.println("Checking boss " + boss);
                            check++;
                        }
                    }
                    catch (Exception e)
                    {
                        System.out.println(e.getMessage());
                        boss = null;
                    }
                    return null;
                }
            });
        }
    }

    public void connectToGaggle() throws Exception {
        try {
            boss = (Boss3) Naming.lookup(uri);
        } catch (Exception ex) {
            System.out.println("EXCEPT MESSAGE: " + ex.getMessage());
            if (ex.getMessage().startsWith("Connection refused to host:")) {
                System.out.println("Couldn't find a boss, trying to start one....");
            }
        }
    }

    /**
     * Try to connect to Gaggle without autostarting Boss.
     */
    public void connectToGaggleIfAvailable() throws Exception {
        boolean autostart = connector.getAutoStartBoss();
        try {
            connector.setAutoStartBoss(false);
            connector.connectToGaggle();
        }
        catch (Exception e) {
            System.out.println("Firegoose tried and failed to connect to Gaggle Boss: " + e.getClass().getName() + ": " + e.getMessage() );
        }
        finally {
            connector.setAutoStartBoss(autostart);
        }

    }

    public void handleNameList(String sourceGooseName, Namelist namelist) throws RemoteException {
    }

    public void handleMatrix(String sourceGooseName, DataMatrix simpleDataMatrix) throws RemoteException {
        //TODO
        System.out.println("incoming broadcast: DataMatrix");
    }


    public void handleTuple(String string, GaggleTuple gaggleTuple) throws RemoteException {
        //TODO
        System.out.println("incoming broadcast: gaggleTuple");
    }

    public void handleCluster(String sourceGooseName, Cluster cluster) throws RemoteException {
    }

    public void handleNetwork(String sourceGooseName, Network network) throws RemoteException {
        System.out.println("incoming broadcast: network");
    }

    public void update(String[] gooseNames) throws RemoteException {
        this.activeGooseNames = gooseNames;
    }

    public String getName() {
        //synchronized (gooseNameObj)
        {
            return gooseName;
        }
    }

    public void setName(String gooseName) {
        // The name will be set to a unique name after the goose is registered with the boss
        //synchronized (gooseNameObj)
        {
            this.gooseName = gooseName;
            System.out.println("Set GaggleProxy Applet name to: " + this.gooseName);
        }
    }


    public Tuple getMetadata() {
        return metadata;
    }

    public void setMetadata(Tuple metadata) {
        this.metadata = metadata;
    }

    public void doBroadcastList() {
        // TODO Auto-generated method stub
    }

    public void doExit() throws RemoteException {
        // TODO Auto-generated method stub
    }

    public void doHide() {
        // TODO Auto-generated method stub
        // could use window.focus() and window.blur() to implement these, if
        // we had a method of calling javascript from java.
        System.out.println("ProxyGoose.doHide()");
    }

    public void doShow() {
        System.out.println("ProxyGoose.doShow()");
    }
}
