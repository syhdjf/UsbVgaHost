package aixinde.usb_vga_host;

import android.app.Activity;
import android.os.ServiceManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import java.util.Arrays;
import java.lang.Thread;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import java.util.HashMap;
import java.util.Iterator;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;

import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Paint;

public class UsbVgaHost extends Activity implements OnClickListener{
	private final static String LOG_TAG = "aixinde.UsbVgaHostActivity";

	private EditText infoText = null;
	private Button startButton = null;
	
	private UsbManager mManager = null; 
	private UsbDevice mUsbDevice = null;
	private UsbInterface mInterface = null;   
	private UsbEndpoint mUsbEndpointOut = null;
	private UsbEndpoint mUsbEndpointIn = null;
    private UsbDeviceConnection mDeviceConnection = null;
	

	private SurfaceView usbVgaSurfaceView = null;
	private SurfaceHolder usbVgaSurfaceHolder = null;

	private byte[] Sendbytes;
	private byte[] Receivebytes;
	private Bitmap usbVgaBitmap = null;
	private int imgWidth, imgHeight; 
	private int times = 0;


	

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);

		Sendbytes = new byte[300];
		Receivebytes = new byte[600];

		infoText = (EditText)findViewById(R.id.text_info);
		infoText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		infoText.setGravity(Gravity.TOP);
		infoText.setSingleLine(false); 
		infoText.setMaxLines(20);
		
		startButton = (Button)findViewById(R.id.button_start);
		startButton.setOnClickListener(this);

		usbVgaSurfaceView = (SurfaceView)findViewById(R.id.surfaceview_usbvga);
		usbVgaSurfaceHolder = usbVgaSurfaceView.getHolder();	
		usbVgaSurfaceHolder.addCallback( new UsbVgaCallBack());
		
		
		/*init the usbhost connection*/
		initUsbHost();
		

		Log.i(LOG_TAG, "UsbVgaTest Activity Created.");
	}

	@Override
	public void onClick(View v){
		if(v.equals(startButton)){
			Log.i(LOG_TAG, "startButton is click.");
			if((null != mDeviceConnection) && (null !=mUsbEndpointIn) && (null != mUsbEndpointOut))
			{
				times = 10;
				Log.i(LOG_TAG, "usbVgaSurfaceHolder.addCallback.");
			}
			else
				Log.e(LOG_TAG, "There is some err for usbvga Connection or Endpoint !");
		}
	}

	private void initUsbHost(){
		/*get the usbManager*/
		mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		if (mManager == null) {
			Log.e(LOG_TAG, "Get usbManager err !");
			return;
		} 
		
		Log.i(LOG_TAG,  String.valueOf(mManager.toString()));
		/*get the device list and check if there is the usbdvga we need*/
		HashMap<String, UsbDevice> deviceList = mManager.getDeviceList();
		Log.i(LOG_TAG,  String.valueOf(deviceList.size()));

		Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();

			if (1204 == device.getVendorId() && 4100 == device.getProductId()) {
				/*get the usbvga*/
				mUsbDevice = device;
				Log.i(LOG_TAG, "find usbvga device.");
				infoText.setText("find usbvga device.");
				/*get the interface and endpoint*/
				findIntfAndEpt();
				break;
			}
		}
	}

	private void findIntfAndEpt() {
		if (mUsbDevice == null) {
			Log.i(LOG_TAG,"No usbvga device find.");
			infoText.setText("No usbvga device find.");
			return;
		}

		/* our device usbvga have only one interface */
		mInterface = mUsbDevice.getInterface(0);

		if (mInterface != null) {
			UsbDeviceConnection connection = null;
			if(mManager.hasPermission(mUsbDevice)) {
				/* get the usbDeviceConnection for data transfer */
				connection = mManager.openDevice(mUsbDevice); 
				if (connection == null) {
					Log.i(LOG_TAG,"Err when get the usbDeviceConnection.");
					infoText.setText(infoText.getText() +"\nErr when get the usbDeviceConnection.");
					return;
				}
				if (connection.claimInterface(mInterface, true)) {
					Log.i(LOG_TAG,"Find out interface for usbvga device.");
					infoText.setText(infoText.getText() +"\nFind out interface for usbvga device.");
					mDeviceConnection = connection;
					/*get the endpoint for data transfer*/
					getEndpoint(mInterface);
				} else {
					connection.close();
				}
			} else {
				Log.i(LOG_TAG,"No permission for usbvga device.");
				infoText.setText(infoText.getText() +"\nNo permission for usbvga device.");
			}
		}
		else {
			Log.i(LOG_TAG,"No interface find in usbvga device.");
			infoText.setText(infoText.getText() +"\nNo interface find in usbvga device.");
		}
	}



	private void getEndpoint(UsbInterface intf) {
		for (int i = 0; i < intf.getEndpointCount(); i++) {
			UsbEndpoint ep = intf.getEndpoint(i);
			if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
				if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
					mUsbEndpointOut = ep;
					infoText.setText(infoText.getText() +"\nFind out input endpoint.");
				} else {
					mUsbEndpointIn = ep;
					infoText.setText(infoText.getText() +"\nFind out output endpoint.");
				}
			}
		}
	}

	/* convert the rgb565 data to bitmap */
	public  static Bitmap getOriginalBitmap(byte[] data, int width, int height)
	{
		try{
			Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
			ByteBuffer buffer = ByteBuffer.wrap(data);
			buffer.position(0);
			bitmap.copyPixelsFromBuffer(buffer);
			buffer.position(0);
			

			return bitmap;
		}catch(Exception e){
			Log.e(LOG_TAG, "Exception while getOriginalBitmap.");
			e.printStackTrace();
		}
		return null;
	}


	class UsbVgaCallBack implements SurfaceHolder.Callback{
		public void surfaceCreated(SurfaceHolder holder){
			Log.i(LOG_TAG, "UsbVgaCallBack is created.");
			new UsbVgaReadAndDrawImage().start();
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {  
        }  
	}

	class UsbVgaReadAndDrawImage extends Thread{
		public void run(){
			while(true){
				while(times > 0){
					//read usbvga data through usb
					try{
						int ret = -1;

						ret = mDeviceConnection.controlTransfer(0x80, 0x06, 0x100, 0x00, Sendbytes, 18, 50000);
						if(ret < 0){
							Log.i(LOG_TAG, "controlTransfer err");
						}
						else{
							Log.i(LOG_TAG, "get usb_device_descriptor success.   ret = "+ret + Arrays.toString(Sendbytes) );
						}
						
						for(int i = 0; i<Sendbytes.length; ++i)
						{
							if (i%2 == 0)
							Sendbytes[i] = (byte)0xe0;
							else
							Sendbytes[i] = (byte)0x07;
						}
						Log.i(LOG_TAG, "Sendbytes:\n"+ Arrays.toString(Sendbytes));

						ret = mDeviceConnection.bulkTransfer(mUsbEndpointOut, Sendbytes, Sendbytes.length, 5000);
						if(ret < 0)
						{
							Log.i(LOG_TAG, "Write to usbvga err.");
						}
						else
						{
							Log.i(LOG_TAG, "Write to usbvga"+ret+ " bytes.");

							
							ret = mDeviceConnection.bulkTransfer(mUsbEndpointIn, Receivebytes, ret, 5000);
							if(ret < 0)
							{
								Log.i(LOG_TAG, "Read from usbvga err.");
							}
							else
							{
								Log.i(LOG_TAG, "Read from usbvga bytes: \n"+ Arrays.toString(Receivebytes));
							}
						}
					}catch(Exception e){
						Log.e(LOG_TAG, "Exception while reading value from usb_vga service.");
						e.printStackTrace();
					}						

					imgWidth = 10;
					imgHeight = 15;
					Bitmap originalBitmap = getOriginalBitmap(Receivebytes, imgWidth, imgHeight);
					usbVgaBitmap = Bitmap.createScaledBitmap(originalBitmap, 1920, 600, true);

					if(null != usbVgaBitmap){
						Canvas c = usbVgaSurfaceHolder.lockCanvas(new Rect(0, 0, 1920, 1080));
						c.drawBitmap(usbVgaBitmap, 0, 0, new Paint());
						usbVgaSurfaceHolder.unlockCanvasAndPost(c);
					}

					times --;
				}
			}

		}
	}

	
}