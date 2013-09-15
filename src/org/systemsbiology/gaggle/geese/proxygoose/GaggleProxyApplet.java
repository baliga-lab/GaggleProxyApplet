// GaggleProxyApplet.java
// by Ning Jiang
// Sep 28, 2012
//
// This applet provides an interface for using true sockets in JavaScript.
//
// Note: You will need to have the Java Plugin archive in your classpath to compile this.
//       For me, that's C:\Program Files\Java\jre6\lib\plugin.jar
// Note: You will need to jar this class and Listener.class into a signed jar file if
//       you want your sockets to access domains other than the one this is running on.
// Note: Apparently, when you grant permissions to Java applets in Java 6, you only grant
//       them to the main applet thread.  That's the reason for all the confusing stuff
//       in the connect methods... so that connections always happen on the main thread.

package org.systemsbiology.gaggle.geese.proxygoose;

import netscape.javascript.JSObject;
import org.systemsbiology.gaggle.core.Boss3;
import org.systemsbiology.gaggle.core.Goose3;
import org.systemsbiology.gaggle.core.datatypes.GaggleData;
import org.systemsbiology.gaggle.core.datatypes.WorkflowAction;
import org.systemsbiology.gaggle.core.datatypes.WorkflowData;

import javax.swing.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.UUID;

public class GaggleProxyApplet extends JApplet {

	// Instance variables
	JSObject browser = null;		// The browser
	Socket socket = null;			// The current socket
	PrintWriter out = null;			// Output
	Listener listener = null;		// Listens for input
	boolean running = false;		// Am I still running?
	String address = null;			// Where you will connect to
	int port = -1;					// Port
	boolean connectionDone = false;	// Thread synchronization
    Object workflowSyncObj = new Object();

    Boss3 boss = null;
    ProxyGoose goose = null;
    BossCallbackGoose callbackGoose = null;
    WorkflowAction action = null;

    // Initialize
	public void init(){
        browser = JSObject.getWindow(this);
	}

	// Stop and destroy
	public void stop(){
		running = false;
		disconnect();
	}
	public void destroy(){
		running = false;
		disconnect();
	}

    public boolean isRunning() { return running; }

    public Goose3 getCallbackGoose() { return callbackGoose; }

	// Main
	// Note: This method loops over and over to handle requests becuase only
	//       this thread gets the elevated security policy.  Java == stupid.
	public void start(){
        System.out.println("Starting Gaggle Proxy...");
        if (browser != null)
		    browser.call("java_socket_bridge_ready", null);
        try
        {
            callbackGoose = new BossCallbackGoose(this, browser, workflowSyncObj);
            goose = new ProxyGoose(this, callbackGoose, browser, workflowSyncObj);
        }
        catch (Exception e)
        {
            System.out.println("Failed to initialize proxy geese " + e.getMessage());
            e.printStackTrace();
        }


        running = true;
		/*while(running){
			// Wait
			try{
				Thread.sleep(100);
			}
			catch(Exception ex){
				running = false;
				return;
			}
			// Connect
			if(address != null && port != -1 && socket == null){
				do_connect(address, port);
			}
		} */
	}

	// Connect
	public boolean connect(String url, int p){
		address = url;
		port = p;
		// Wait for the connection to happen in the main thread
		connectionDone = false;
		while(!connectionDone){
			try{ Thread.sleep(100); }
			catch(Exception ex){ return false; }
		}
		connectionDone = false;
		// Did it work?
		if(socket != null) return true;
		return false;
	}
	private void do_connect(String url, int p){
		if(socket == null){
			try{
				socket = new Socket(url, p);
				out = new PrintWriter(socket.getOutputStream());
				listener = new Listener(socket, this);
				listener.start();
				log("Java Socket Bridge CONNECTED: "+getUrl());
			}
			catch(Exception ex){
				error("Could not connect to "+url+" on port "+p+"\n"+ex.getMessage());
				connectionDone = true;
			}
		}
		else{
			error("Already connected to "+getUrl());
		}
		connectionDone = true;
	}

	// Disconnect
	public boolean disconnect(){
		if(socket != null){
			try{
				log("Java Socket Bridge DISCONNECTED: "+getUrl());
				listener.close();
				out.close();
				socket = null;
				address = null;
				port = -1;
				return true;
			}
			catch(Exception ex){
				error("An error occured while closing the socket\n"+ex.getMessage());
				socket = null;
				return false;
			}
		}
		return false;
	}

	// Send a message
	public boolean send(String message){
		if(out != null){
			try{
				out.println(message);
				out.flush();
				log("Java Socket Bridge SENT: "+message);
			}
			catch(Exception ex){
				error("Could not write to socket\n"+ex.getMessage());
			}
			return true;
		}
		else{
			error("Not connected");
			return false;
		}
	}

	// Get input from the socket
	public void hear(String message){
		Object[] arguments = new Object[1];
		arguments[0] = message;
		browser.call("on_socket_get", arguments);
		log("Java Socket Bridge RECEIVED: "+message);
	}

    public boolean ConnectGaggle()
    {
        System.out.println("Connecting to Gaggle...");
        try {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try
                    {
                        goose.connectToGaggle();
                    }
                    catch (RemoteException e)
                    {
                        System.out.println(e.getMessage());
                    }
                    catch (Exception ex)
                    {
                        System.out.println("General exception: " + ex.getMessage());
                    }
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }
            });
            return true;

        } catch (Exception ex) {
            if (ex.getMessage().startsWith("Connection refused to host:")) {
                System.out.println("Couldn't find a boss, trying to start one....");
            }
            else
            {
                ex.printStackTrace();
            }
        }
        return false;
    }


    /*public String[] getGeeseList()
    {
        try
        {
            goose.connectToGaggle();
            return goose.activeGooseNames;
        }
        catch (Exception e)
        {
            System.out.println("Failed to get geese list: " + e.getMessage());
        }
        return null;
    } */

    public void SubmitWorkflow(String jsonWorkflow)
    {
        synchronized (workflowSyncObj)
        {
            System.out.println("Workflow received: " + jsonWorkflow);
            this.goose.SubmitWorkflow(jsonWorkflow);
        }
    }

    public void SaveStateDelegate(String userid, String name, String desc)
    {
        System.out.println("Save state for  " + userid);

        if (userid != null)
        {
            this.goose.saveStateDelegate(userid, name, desc);
        }
    }

    public void LoadStateDelegate(String stateid)
    {
        System.out.println("Load state for " + stateid);
        if (stateid != null)
        {
            this.goose.loadStateDelegate(stateid);
        }
    }

    public void UploadFiles(String jsonUploadString)
    {
        System.out.println("Upload file to boss " + jsonUploadString);
        WorkflowData data = new WorkflowData(jsonUploadString);
        GaggleData[] items = new GaggleData[1];
        items[0] = data;
        WorkflowAction workflowAction = new WorkflowAction(null, null, null,
                WorkflowAction.ActionType.Request, null, null, WorkflowAction.Options.FileUploadRequest.getValue(), items);
        this.goose.uploadFiles(workflowAction);
    }


    public String StartRecording()
    {
        System.out.println("Proxy Applet StartRecording");
        UUID rid = this.goose.startRecording();
        if (rid != null)
        {
            return rid.toString();
        }
        return null;
    }

    public String PauseRecording(String uuid)
    {
        if (uuid != null)
        {
            System.out.println("Proxy Applet PauseRecording");
            UUID rid = UUID.fromString(uuid);
            String jsonworkflow = this.goose.pauseRecording(rid);
            System.out.println("Workflow: " + jsonworkflow);
            return jsonworkflow;
        }
        return null;
    }

    public void ResumeRecording(String uuid)
    {
        if (uuid != null)
        {
            System.out.println("Proxy Applet ResumeRecording");
            UUID rid = UUID.fromString(uuid);
            this.goose.resumeRecording(rid);
        }
    }

    public String StopRecording(String uuid)
    {
        if (uuid != null)
        {
            System.out.println("Proxy Applet StopRecording");
            UUID rid = UUID.fromString(uuid);
            String jsonworkflow = this.goose.stopRecording(rid);
            System.out.println("Workflow: " + jsonworkflow);
            return jsonworkflow;
        }
        return null;
    }

    public boolean ProcessAction(String sourcename, String sourcecommand, String targetname, String targetcommand, int edgetype)
    {
        /*try
        {
            System.out.println("Starting action for " + sourcename);

            WorkflowComponent source = new WorkflowComponent(sourcename, "", sourcecommand, null);
            WorkflowComponent target = new WorkflowComponent(targetname, "", targetcommand, null);
            action = new WorkflowAction(source,target, 1, null);
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try
                    {
                        boss.broadcastWorkflowAction(action);
                    }
                    catch (RemoteException e)
                    {
                        System.out.println(e.getMessage());
                    }
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }
            });
            return true;
        }
        catch (Exception ex)
        {
            System.out.println(ex.getMessage());
        }        */
        return false;
    }

    public String[] getGeeseNames()
    {
        return this.goose.getGooseNames();
    }

	// Report an error
	public void error(String message){
		message = "Java Socket Bridge ERROR: " + message;
		log(message);
		Object[] arguments = new Object[1];
		arguments[0] = message;
		browser.call("on_socket_error", arguments);
	}

	// Log something
	public void log(String message){
		System.out.println(message);
	}

	// Get the connected URL
	private String getUrl(){
		if(socket == null) return null;
		return socket.getInetAddress().getHostName() +":"+socket.getPort();
	}
}