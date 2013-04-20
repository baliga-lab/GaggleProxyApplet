/*
 * Copyright (C) 2007 by Institute for Systems Biology,
 * Seattle, Washington, USA.  All rights reserved.
 *
 * This source code is distributed under the GNU Lesser
 * General Public License, the text of which is available at:
 *   http://www.gnu.org/copyleft/lesser.html
 */

package org.systemsbiology.gaggle.geese.proxygoose;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.*;

//import org.apache.commons.logging.Log;
import net.sf.json.JSONObject;
import netscape.javascript.JSObject;
import org.systemsbiology.gaggle.core.*;
import org.systemsbiology.gaggle.core.datatypes.*;
import org.systemsbiology.gaggle.geese.common.GaggleConnectionListener;
import org.systemsbiology.gaggle.geese.common.RmiGaggleConnector;
import org.systemsbiology.gaggle.geese.common.GooseShutdownHook;

import javax.print.attribute.standard.DateTimeAtCompleted;


/**
 * The FireGoose class is the java side of the Firefox Gaggle
 * toolbar.
 *
 * @author cbare
 */
public class ProxyGoose implements Goose3, GaggleConnectionListener {
    String activeGooseNames[] = new String[0];
    RmiGaggleConnector connector = new RmiGaggleConnector(this);
    final static String defaultGooseName = "ProxyAppletGoose";
    String gooseName = defaultGooseName;
    private String uri = "rmi://localhost/gaggle";
    Boss3 boss;
    UUID recordSessionID = null;
    String jsonRecordedWorkflow = null;

    Signal hasNewDataSignal = new Signal();
    Signal hasTargetUpdateSignal = new Signal();

    String species = "unknown";
    String[] nameList;
    String size;
    String type = null;
    Tuple metadata;

    String workflowString;
    String jsongooseinfo;
    Goose3 self = null;
    JSObject browser;


    public ProxyGoose(JSObject browser) {
        try
        {
            this.browser = browser;
            connector.setAutoStartBoss(true);
            connector.addListener(this);
            new GooseShutdownHook(connector);
            self = this;
        }
        catch (Exception e)
        {
            System.out.println("Error starting RMI connector: " + e.getMessage());
        }

        System.out.println("created ProxyGoose instance");
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String[] getNameList() {
        return nameList;
    }

    /**
     * Used to implement a FG_GaggleData object that represents the
     * broadcast from the Gaggle.
     * See FG_GaggleDataFromGoose in firegoose.js
     * @return a type
     */
    public String getType() {
		return type;
	}

    public String getSize() {
    	return size;
    }

    public void test(Object object) {
    	// this finally worked somehow:
    	// it's an example of calling into javascript from java
    	// this works w/ the apple MRJPlugin implementation of JSObject
    	// but not with the sun implementation found on windows.
    	if (object == null) {
    		System.out.println("Hey that tickles! It's a null!");
    	}
    	else {
    		System.out.println("I got a " + object.getClass().getName());
    		System.out.println("This object has a name: " + ((netscape.javascript.JSObject)object).getMember("name"));
    		((netscape.javascript.JSObject)object).call("test",new Object[] {});
    		System.out.println("did that do anything?");
    	}
    }


    /**
     * a hacky way to signal that we have received a new broadcast
     * from the Gaggle, so we don't have to keep updating. The idea is to
     * compare the return value with the value you got last time. If the
     * value has changed, we got a broadcast since last time you checked.
     * @return an integer that increases every time we get a broadcast.
     */
    public int checkNewDataSignal() {
    	return hasNewDataSignal.check();
    }
    
    public int checkTargetUpdateSignal() {
    	return hasTargetUpdateSignal.check();
    }

    public String[] getGooseNames() {
        List<String> results = new ArrayList<String>();
            for (String name : activeGooseNames) {
                if (!this.gooseName.equals(name)) {
                    results.add(name);
                }
            }

        return results.toArray(new String[0]);
    }

    public void broadcastNameList(String targetGoose, String name, String species, String[] names) {
        try {
            Namelist namelist = new Namelist();
            namelist.setName(name);
            namelist.setSpecies(species);
            namelist.setNames(names);
            boss.broadcastNamelist(gooseName, targetGoose, namelist);
        }
        catch (RemoteException e) {
        	System.out.println("FireGoose: rmi error calling boss.broadcastNamelist");
        }
        catch (Exception e) {
            System.out.println(e);        	
        }
    }

    public void broadcastNetwork(String targetGoose, Network network) {
        try {
            boss.broadcastNetwork(gooseName, targetGoose, network);
        }
        catch (RemoteException e) {
        	System.out.println("FireGoose: rmi error calling boss.broadcastNetwork");
            System.out.println(e);
        }
        catch (Exception e) {
            System.out.println(e);        	
        }
    }

    public void broadcastDataMatrix(String targetGoose, DataMatrix matrix) {
        try {
            boss.broadcastMatrix(gooseName, targetGoose, matrix);
        }
        catch (RemoteException e) {
        	System.out.println("FireGoose: rmi error calling boss.broadcastMatrix");
            System.out.println(e);
        }
        catch (Exception e) {
            System.out.println(e);        	
        }
    }

    public void broadcastMap(String targetGoose, String species, String name, HashMap<String, String> map) {
    	System.out.println("broadcastMap not implemented");
//        try {
//            boss.broadcast(gooseName, targetGoose, species, name, map);
//        }
//        catch (RemoteException e) {
//            System.err.println("SampleGoose: rmi error calling boss.broadcast (map)");
//            System.out.println(e);
//        }
    }

    public void broadcastCluster(String targetGoose, String species, String name, String [] rowNames, String [] columnNames) {
        try {
        	Cluster cluster = new Cluster(name, species, rowNames, columnNames);
            boss.broadcastCluster(gooseName, targetGoose, cluster);
        }
        catch (RemoteException e) {
            System.err.println("FireGoose: rmi error calling boss.broadcast (map)");
            System.out.println(e);
        }
    }

    public void showGoose(String gooseName) {
        try {
            boss.show(gooseName);
        }
        catch (RemoteException e) {
        	System.out.println("FireGoose: rmi error calling boss.show (gooseName)");
            System.out.println(e);
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    public void hideGoose(String gooseName) {
        try {
            boss.hide(gooseName);
        }
        catch (RemoteException e) {
        	System.out.println("FireGoose: rmi error calling boss.hide (gooseName)");
            System.out.println(e);
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }


    public void disconnectFromGaggle() {
        connector.disconnectFromGaggle(true);
    }


    public void setAutoStartBoss(boolean autoStartBoss) {
    	this.connector.setAutoStartBoss(autoStartBoss);
    }
    
    public boolean getAutoStartBoss() {
    	return this.connector.getAutoStartBoss();
    }


    // Goose methods ---------------------------------------------------------
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


    public void saveStateDelegate(final String userid, final String name, final String desc)
    {
        System.out.println(userid);
        // Testing purpose, remove later !!!
        //jsonWorkflow = "{type: 'workflow', gaggle-data: 'jsonWorkflow'}";

        connectToBoss();

        if (boss != null)
        {
            try
            {
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try
                        {
                            SimpleDateFormat df = new SimpleDateFormat("MMddyyyy-HHmmss");
                            Date date = new Date();
                            System.out.println("Save state " + userid + " " + df.format(date));
                            boss.saveState(self, userid, name, desc, df.format(date));
                        }
                        catch (Exception ex)
                        {
                            boss = null;
                            System.out.println("Failed to save state " + ex.getMessage());
                        }
                        return null;
                    }
                });
            }
            catch (Exception e1)
            {
                System.out.println(e1.getMessage());
            }
        }
    }

    public void loadStateDelegate(final String stateid)
    {
        System.out.println(stateid);
        connectToBoss();

        if (boss != null)
        {
            try
            {
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try
                        {
                            System.out.println("Loading state ...");
                            boss.loadState(stateid);
                        }
                        catch (Exception ex)
                        {
                            boss = null;
                            System.out.println("Failed to save state " + ex.getMessage());
                        }
                        return null;
                    }
                });
            }
            catch (Exception e1)
            {
                System.out.println(e1.getMessage());
            }
        }
    }



    /***
     * Handles the status info of the workflow (e.g. currently active node, error info etc)
     * @param type
     * @param info
     */
    public void handleWorkflowInformation(String type, String info)
    {
         if (type.equalsIgnoreCase("message"))
         {
             if (info.equalsIgnoreCase("Workflow Finished"))
             {
                 System.out.println("Proxy got workflow finish notification");
                 browser.call("OnWorkflowFinished", null);
                 System.out.println("Submit workfow returned: " + jsongooseinfo);
             }
         }
         else if (type.equalsIgnoreCase("Recording")) {
             System.out.println("Received broadcast recording: " + info);
             //Object[] params = info.split(";");
             browser.call("UpdateRecordingInfo", new Object[]{info});
         }
         else if (type.equalsIgnoreCase("SaveStateResponse"))
         {
             System.out.println("Received save state response: " + info);
             JSONObject jsonObject = JSONObject.fromObject(info);
             JSONObject stateObj = jsonObject.getJSONObject("state");
             String id = stateObj.getString("id");
             String name = stateObj.getString("name");
             String desc = stateObj.getString("desc");
             System.out.println("ID " + id + " name " + name + " desc " + desc);
             //Object[] params = info.split(";");
             browser.call("OnSaveState", new String[]{(id + ";;" + name + ";;" + desc)});
         }
    }

    public void handleTable(String source, Table table)
    {

    }

    public String SubmitWorkflow(String jsonWorkflow)
    {
        System.out.println(jsonWorkflow);
        // Testing purpose, remove later !!!
        //jsonWorkflow = "{type: 'workflow', gaggle-data: 'jsonWorkflow'}";
        this.workflowString = jsonWorkflow;
        int retries = 0;
        while (retries < 2)
        {
            connectToBoss();

            if (boss != null)
            {
                try
                {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            try
                            {
                                System.out.println("Submitting workflow...");
                                String json = boss.submitWorkflow(self, workflowString);
                                System.out.println("Submit Workflow returned " + json);
                                jsongooseinfo = new String(json);
                            }
                            catch (Exception ex)
                            {
                                boss = null;
                                System.out.println("Failed to submit workflow " + ex.getMessage());
                            }
                            return null;
                        }
                    });
                }
                catch (Exception e1)
                {
                    System.out.println(e1.getMessage());
                }
                if (boss == null)
                    System.out.println("One more try...");
                else
                    break;
            }
            else
                break;
            retries++;
        }
        System.out.println("Returning " + jsongooseinfo);
        return jsongooseinfo;
    }

    public UUID startRecording()
    {
        System.out.println("startRecording");
        connectToBoss();
        if (boss != null)
        {
            try
            {
                System.out.println("Calling boss to start recording...");
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try
                        {
                            recordSessionID = boss.startRecordingWorkflow();
                        }
                        catch (Exception e)
                        {
                            System.out.println(e.getMessage());
                        }
                        return null;
                    }
                });
                return recordSessionID;
            }
            catch (Exception e)
            {
                System.out.println(e.getMessage());
            }
        }
        return null;
    }

    public String stopRecording(final UUID rid)
    {
        System.out.println("stopRecording");
        connectToBoss();
        if (boss != null)
        {
            try
            {
                System.out.println("Calling boss to stop recording...");
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try
                        {
                            jsonRecordedWorkflow = boss.terminateRecordingWorkflow(rid);
                        }
                        catch (Exception e)
                        {
                            System.out.println(e.getMessage());
                        }
                        return null;
                    }
                });
                //System.out.println("Recorded workflow: " + jsonRecordedWorkflow);
                return jsonRecordedWorkflow;
            }
            catch (Exception e)
            {
                System.out.println(e.getMessage());
            }
        }
        return null;
    }

    public String pauseRecording(final UUID rid)
    {
        System.out.println("pauseRecording");
        connectToBoss();
        if (boss != null)
        {
            try
            {
                System.out.println("Calling boss to pause recording...");
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try
                        {
                            jsonRecordedWorkflow = boss.pauseRecordingWorkflow(rid);
                        }
                        catch (Exception e)
                        {
                            System.out.println(e.getMessage());
                        }
                        return null;
                    }
                });
                //System.out.println("Recorded workflow: " + jsonRecordedWorkflow);
                return jsonRecordedWorkflow;
            }
            catch (Exception e)
            {
                System.out.println(e.getMessage());
            }
        }
        return null;
    }

    public String resumeRecording(final UUID rid)
    {
        System.out.println("resumeRecording");
        connectToBoss();
        if (boss != null)
        {
            try
            {
                System.out.println("Calling boss to resume recording...");
                AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try
                        {
                            boss.resumeRecordingWorkflow(rid);
                        }
                        catch (Exception e)
                        {
                            System.out.println(e.getMessage());
                        }
                        return null;
                    }
                });
            }
            catch (Exception e)
            {
                System.out.println(e.getMessage());
            }
        }
        return null;
    }

    private void connectToBoss()
    {
        if (boss == null)
        {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try
                    {
                        connectToGaggle();
                        boss = (org.systemsbiology.gaggle.core.Boss3)connector.getBoss();
                    }
                    catch (Exception e)
                    {
                        System.out.println(e.getMessage());
                    }
                    return null;
                }
            });
        }
    }

    public void connectToGaggle() throws Exception {
    	try {
            connector.connectToGaggle();
    	}
    	catch (Exception e) {
    		System.out.println("Exception trying to connect to Boss:");
    		e.printStackTrace();
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
        this.species = namelist.getSpecies();
        this.nameList = namelist.getNames();
        this.type = "NameList";
        this.size = String.valueOf(nameList.length);
        hasNewDataSignal.increment();
        System.out.println("incoming broadcast: " + type + "(" + size + ")");
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
    	// we handle clusters by translating them to namelists
        this.species = cluster.getSpecies();
        this.nameList = cluster.getRowNames();
        this.type = "NameList";
        this.size = String.valueOf(nameList.length);
        hasNewDataSignal.increment();
        System.out.println("incoming broadcast: cluster translated to " + type + "(" + size + ")");
    }

    public void handleNetwork(String sourceGooseName, Network network) throws RemoteException {
        System.out.println("incoming broadcast: network");
    }

    public void update(String[] gooseNames) throws RemoteException {
        this.activeGooseNames = gooseNames;
        this.hasTargetUpdateSignal.increment();
    }

    public String getName() {
        return gooseName;
    }

    public void setName(String gooseName) {
        // The name will be set to a unique name after the goose is registered with the boss
        this.gooseName = gooseName;
        System.out.println("Set GaggleProxy Applet name to: " + this.gooseName);
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

    public String[] getSelection() {
        // TODO Auto-generated method stub
        return null;
    }

    public int getSelectionCount() {
        // TODO Auto-generated method stub
        return 0;
    }

    public void clearSelections() {
        // TODO Auto-generated method stub
    }

    //implements GaggleConnectionListener
	public void setConnected(boolean connected, Boss boss) {
		if (connected) {
            if (boss.getClass().isInstance(Boss3.class))  // if the boss is an earlier version, we ignore it.
			    this.boss = (Boss3)boss;
            else
                this.boss = null;
		}
		else {
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






    /*
    public void handleMap(String species, String dataTitle, HashMap hashMap) {
        this.species = species;
        this.dataTitle = dataTitle;
        this.map = hashMap;
        this.incomingDataMsg = "Map(" + hashMap.size() + ")";
    }

    public void handleMatrix(DataMatrix matrix) throws RemoteException {
        this.species = matrix.getSpecies();
        this.dataMatrix = matrix;
        this.incomingDataMsg = "Matrix(" + matrix.getRowCount() + "x" + matrix.getColumnCount() + ")";
    }

    public void handleNetwork(String species, Network network) throws RemoteException {
        this.species = species;
        this.network = network;
        this.incomingDataMsg = "Network(" + network.nodeCount() + "x" + network.edgeCount() + ")";
    }
    */


    // end Goose methods -----------------------------------------------------

	/**
	 * A signal to tell when a new broadcast from the Gaggle has arrived. Dunno
	 * if this really helps thread safety much, but it's an effort in that direction.
	 */
	private static class Signal {
		private int value = 0;

		/**
		 * @return the value of the signal.
		 */
		public synchronized int check() {
			return value;
		}

		public synchronized void reset() {
			value = 0;
		}

		/**
		 * increment the value of the signal.
		 */
		public synchronized void increment() {
			value++;
		}
	}
}
