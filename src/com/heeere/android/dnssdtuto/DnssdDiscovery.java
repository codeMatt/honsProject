package com.heeere.android.dnssdtuto;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.Formatter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class DnssdDiscovery extends Activity {

	//instance variables for handling jmdns
    android.net.wifi.WifiManager.MulticastLock lock;
    android.os.Handler handler = new android.os.Handler();
    EditText nameText;
    ArrayList<DeviceInstance> deviceList = new ArrayList<DeviceInstance> ();
    int deviceCount = 0;
    ArrayAdapter<DeviceInstance> spinnerArrayAdapter;       
    
    //called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        //acquire lock for device discovery
        android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)
        		getSystemService(android.content.Context.WIFI_SERVICE);
        
        lock = wifi.createMulticastLock("mylockthereturn");
        lock.setReferenceCounted(true);
        lock.acquire();

        //UI Features------------------------------------------------
        //enter device name textbox
        nameText = (EditText) findViewById(R.id.nameString); 
        
        //register your service button ----------------------------
        Button registerButton = (Button) findViewById (R.id.button1);
        registerButton.setOnClickListener(new OnClickListener() {
        	 
			@Override
			public void onClick(View arg0) {
				//setName();
				deviceName = (String)nameText.getText().toString();
				try {
					register();
				} catch (IOException e) {
					e.printStackTrace();
				}
				showIP();
			} 
		});      
        
        //get file button  ------------------------------
        Button getFileButton = (Button) findViewById (R.id.button2);
        getFileButton.setOnClickListener(new OnClickListener() {
       	 
			@Override
			public void onClick(View arg0) {
				try{
					
				EditText address = (EditText) findViewById (R.id.addressString);   
				String[] tempAddress = address.getText().toString().split(":");				
				//int port = Integer.parseInt(tempAddress[1]);
				String[] files = {"newTestFile.txt"};
				initiateFileTransfer(tempAddress[0], 5000, files);
				
				}catch(Exception e){
					notifyUser("Please enter the address correctly");
				}
			} 
		});       
 
        //get multiple files button ------------------------------
        Button getFilesButton = (Button) findViewById (R.id.button3);
        getFilesButton.setOnClickListener(new OnClickListener() {
       	 
			@Override
			public void onClick(View arg0) {	
				try{
					
				EditText address = (EditText) findViewById (R.id.addressString);
				String[] files = { "newTestFile.txt", "secondFile.txt"};				
				String[] tempAddress = address.getText().toString().split(":");				
				//int port = Integer.parseInt(tempAddress[1]);				
				initiateFileTransfer(tempAddress[0], 5000, files);
				
				}catch(Exception e){
					notifyUser("Please enter the address correctly");
				}
			} 
		});
       
        new Thread(){
        	public void run() {        		
        		setUp();        		
        		}
        	}.start();
        	
       createDummyFile();
        //-------------------------------------------------------------
        
        //handler.postDelayed(new Runnable() {
           // public void run() {
            //    setUp();
           // }
           // }, 1000);

    }    //Called when the activity is first created.

    private String type = "_share._tcp.local.";
    private JmDNS jmdns = null;
    private ServiceListener listener = null;
    private ServiceInfo serviceInfo;
    private String deviceName;
    
    //method to set up the jmdns service and service listener
	private void setUp() {
		        
        try {

            jmdns = JmDNS.create(); 
            jmdns.addServiceListener(type, listener = new ServiceListener() {
            	
            	public String[] serviceUrls = null;
            	
                @Override
                public void serviceResolved(ServiceEvent ev) {                	                   
                	serviceUrls = ev.getInfo().getURLs();    
                	//addDeviceToList(ev.getName(), serviceUrls[0], ev.getInfo().getPort());
                	deviceList.add(new DeviceInstance (ev.getName(), serviceUrls[0], ev.getInfo().getPort()));
                	
                	notifyUser("New Service Available: " + ev.getName() + " Address: " + serviceUrls[0]);
                }

                @Override
                public void serviceRemoved(ServiceEvent ev) {
                	notifyUser("Service removed: " + ev.getName());
                }

                @Override
                public void serviceAdded(ServiceEvent event) {
                	notifyUser("Service added:");
                    // Required to force serviceResolved to be called again (after the first search)
                    jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
                }
            });
            
            
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
	
	//method to show the ip from the wifimanager
	
	//method to show the IP of the device through the wifimanager
	public void showIP(){
		//print out the IP address of the local device
		WifiManager wim= (WifiManager) getSystemService(WIFI_SERVICE);
		List<WifiConfiguration> l =  wim.getConfiguredNetworks();
		WifiConfiguration wc = l.get(0); 
		notifyUser("\n"+ Formatter.formatIpAddress(wim.getConnectionInfo().getIpAddress()));
	}
	
	
	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
	}

	//method to set up the registration of a jmdns service
	//and the socket host
	public void register() throws IOException{

		final int port = 5000;
		
		//register service on jmdns protocol
		serviceInfo = ServiceInfo.create("_share._tcp.local.", deviceName, port, "share service");
		jmdns.registerService(serviceInfo);

		//initialise the socket listener thread that sends file to the port opened
		Thread fileReceiving = new Thread (){

			private ServerSocket connection = new ServerSocket(port);

			public void run(){
				
				Socket sock = null;
				ObjectInputStream ois = null;
				FileInputStream fis = null;
				BufferedInputStream bis = null;
				
				try{	
					while (true) {

						//accept a socket connection from remote location
						sock = connection.accept();
						notifyUser("New socket accepted");
						
						//receive message from other device
						ois = new ObjectInputStream(sock.getInputStream());
						//ois.skip(Long.MAX_VALUE);
						String receivedmessage = (String) ois.readObject();
						System.out.println("from other device: " + receivedmessage);
						String[] messages = receivedmessage.split("-");
						
						File path = getFilesDir();
						
						while(!(messages[0].equals("finish"))){

							//set up file
							path.mkdirs();
							File myFile = new File (path, messages[1]);
							System.out.println("File exists? " + myFile.exists());

							// read data from file into byte array
							byte [] mybytearray = new byte [(int)myFile.length()];
							fis = new FileInputStream(myFile);
							bis = new BufferedInputStream(fis);
							bis.read(mybytearray,0,mybytearray.length);	

							//System.out.println(messages[0]);
							//sends EOF on outputstream so that new outputstream can be opened
							//sock.shutdownOutput();

							// send data to other device from bytearray
							OutputStream os = sock.getOutputStream();
							os.write(mybytearray,0,mybytearray.length);
							os.flush();

							//send EOF of data stream
							//sock.shutdownOutput();
							System.out.println("sent File, receiving text message");

							//receive message from other device
							ObjectInputStream ois2 = new ObjectInputStream(sock.getInputStream());
							//ois2.skip(Long.MAX_VALUE);
							receivedmessage = (String) ois2.readObject();
							System.out.println("from other device: " + receivedmessage);
							messages = receivedmessage.split("-");
						}
						
						notifyUser("all files transferred...");
						System.out.println("all files transferred...");


					}
				}catch (UnknownHostException e) {
					System.out.println("Unknown host exception");
					e.printStackTrace();
				} catch (IOException e) {
					System.out.println("Error sending file, IOException");
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					System.out.println("Error receiving confirmation, ClassNotException");
					e.printStackTrace();
				}
				finally{
					System.out.println("entered finally block");
					//house cleaning of streams and socket
					notifyUser("closing connections");
					try{
					bis.close();
					ois.close();
					fis.close();									
					sock.close();
					
					
					} catch(IOException e){
						System.out.println("Error closing streams, IOException");
						e.printStackTrace();
					}
				}
			}
		};
		fileReceiving.start();
	}

	//Initialize the socket connection and file transfer
	public void initiateFileTransfer(final String address, final int port, final String[] fileList){

			
			notifyUser("File receiving initiated From: " + address);
	
			Thread fileSending = new Thread (){
				public void run(){
					
					//Opening The socket & sending the file----------------------------------------------------------------------------------------
					//System.out.println("Starting file sending process");  		

					Socket connection = null;
					ObjectOutputStream oos = null;
					BufferedOutputStream bos = null;
					FileOutputStream fos= null;

					try {
						//file size hardcoded
						int filesize = 900000;
						long start = System.currentTimeMillis();
						int bytesRead, current = 0;
						
						int filesLeft = fileList.length;
						int currentFile = 0;
						
						connection = new Socket (address, port);  		
						byte [] mybytearray = new byte [filesize];						

						
						while(filesLeft > 0){

							//Sending message to device
							String message = "get-" + fileList[currentFile++];
							
							oos = new ObjectOutputStream(connection.getOutputStream());
							oos.writeObject(message);
							oos.flush();
							
							System.out.println("getting file: " + fileList[currentFile-1]);
							
							//stream managements
							InputStream is = connection.getInputStream();
							System.out.println("inputstream instantiated: ");
							
							File receivedFile = new File(getFilesDir(), "/received" + currentFile + ".txt");
							
							fos = new FileOutputStream(receivedFile);
							bos = new BufferedOutputStream(fos);

							//receive data from stream
							System.out.println("data reading started");
							int count;
							
							while ((count = is.read(mybytearray)) >= 0) {
								//System.out.println(count);
								is.read(mybytearray, count, (mybytearray.length-count));
								bos.write(mybytearray, 0, count);
							}
							
							/*bytesRead = is.read(mybytearray, 0 , mybytearray.length);
							current = bytesRead;
							do {							
								bytesRead = is.read(mybytearray, current, (mybytearray.length-current));						
								if(bytesRead >= 0)
									current += bytesRead;						
							} while(bytesRead > -1);*/
							System.out.println("Data reading complete");
							
							//write data to array
							//bos.write(mybytearray, 0 , current);
							bos.flush();
							long end = System.currentTimeMillis();
							System.out.println("time taken = " + String.valueOf(end-start) + "ms");
							filesLeft--;
						}
					    
						//Sending message to device to end session
						String message = "finish-xx";
						
						oos = new ObjectOutputStream(connection.getOutputStream());
						oos.writeObject(message);
						oos.flush();
						
						
					} catch (FileNotFoundException e) {
						System.out.println("FileNotFound exception");
						e.printStackTrace();
					}catch (UnknownHostException e) {
						System.out.println("Unknown host exception");
						e.printStackTrace();
					} catch (IOException e) {
						System.out.println("IOException!");
						e.printStackTrace();
					}
					finally{
						//house cleaning of streams and socket
						try{
							
							notifyUser("closing connections");
							
							oos.close();
							bos.close();
							fos.close();
							connection.close();
							
						
						} catch(IOException e){
							System.out.println("Error closing streams, IOException");
							e.printStackTrace();
						}
					}
				}
			};
			fileSending.start();
		}
	
	//write a string to screen
 
	//print text to screen
	private void notifyUser(final String msg) {
    	
      handler.postDelayed(new Runnable() {
            public void run() {
            	TextView t = (TextView)findViewById(R.id.text);
            	t.setText(t.getText()+"\n-"+msg);
            	}
            }, 1);

    }
    
    private void createDummyFile(){
    	
    	
    	String text = "This is a tester file created in the app dnssddemo";
		File path = getFilesDir();
		File file = new File (path, "newTestFile.txt");
		File file2 = new File (path, "secondFile.txt");
    	
    	try {
    		byte[] data = text.getBytes("UTF8");    		
			file.createNewFile();
			file2.createNewFile();
			if(file.exists() && file2.exists())
			{
			     OutputStream fo = new FileOutputStream(file);              
			     fo.write(data);
			     fo.close();
			     
			     OutputStream fo2 = new FileOutputStream(file2);              
			     fo2.write(data);
			     fo2.close();
			     System.out.println("2 files created: ");
			     
			}			
		} catch (FileNotFoundException e) {
			System.out.println("dummy file creation failed");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("dummy file creation failed");
			e.printStackTrace();
		}
    }

    @Override
    protected void onStart() {
        super.onStart(); 
    }

    @Override
    protected void onStop() {
    	if (jmdns != null) {
            if (listener != null) {
                jmdns.removeServiceListener(type, listener);
                listener = null;
            }
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
            } catch (IOException e) {
                System.out.println("Error closing jmdns");
                e.printStackTrace();
            }
            jmdns = null;
    	}
    	//repo.stop();
        //s.stop();
        lock.release();
    	super.onStop();
    }
}