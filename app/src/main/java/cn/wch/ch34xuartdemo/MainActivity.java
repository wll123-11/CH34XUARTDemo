package cn.wch.ch34xuartdemo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


import cn.wch.ch34xuartdemo.entity.EPC;
import cn.wch.ch34xuartdemo.entity.JsonEntity;
import cn.wch.ch34xuartdemo.entity.SerialBaudBean;
import cn.wch.ch34xuartdemo.utils.ToastUtil;
import cn.wch.uartlib.WCHUARTManager;
import cn.wch.uartlib.callback.IDataCallback;
import cn.wch.uartlib.callback.IUsbStateChange;
import cn.wch.uartlib.callback.IModemStatus;
import cn.wch.uartlib.chipImpl.SerialErrorType;
import cn.wch.uartlib.chipImpl.type.ChipType2;
import cn.wch.uartlib.exception.ChipException;
import cn.wch.uartlib.exception.NoPermissionException;
import cn.wch.uartlib.exception.UartLibException;
import cn.wch.ch34xuartdemo.adapter.DeviceAdapter;
import cn.wch.ch34xuartdemo.entity.DeviceEntity;
import cn.wch.ch34xuartdemo.entity.ModemErrorEntity;
import cn.wch.ch34xuartdemo.entity.SerialEntity;
import cn.wch.ch34xuartdemo.ui.CustomTextView;
import cn.wch.ch34xuartdemo.ui.DeviceListDialog;
import cn.wch.ch34xuartdemo.ui.GPIODialog;
import cn.wch.ch34xuartdemo.utils.FormatUtil;
import cn.wch.ch34xuartdemo.utils.Trans;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity {
    RecyclerView deviceRecyclerVIew;
    DeviceAdapter deviceAdapter;
    private Context context;

    private SerialEntity serialEntity;
    public Button start_btn;
    private  ArrayList<EPC> epcList;
    public byte[] newepc = new byte[12];
    public int epcflag = 0 ;//epcflag == 0:没有前半截标签 ；== 1 ：有前半截标签；
    public String EPChalf ="";
    Gson gson=new Gson();
    ArrayList<JsonEntity> jsonEntitylist = new ArrayList<>();
    String selectedtLab;


    //接收区
    TextView readBuffer;
    CustomTextView clearRead;
    TextView readCount;
    //保存各个串口的接收计数
    HashMap<String, Integer> readCountMap = new HashMap<>();
    //已打开的设备列表
    final Set<UsbDevice> devices = Collections.synchronizedSet(new HashSet<UsbDevice>());
    //读线程
    boolean flag = false;

    //接收文件测试。文件默认保存在-->内部存储\Android\data\cn.wch.wchuartdemo\files\下
    private static boolean FILE_TEST = false;

    private static final String TAG = "okhttp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.context = this;
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (!UsbFeatureSupported()) {
            showToast("系统不支持USB Host功能");
            System.exit(0);
            return;
        }
        initUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_maim, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.enumDevice) {
            enumDevice();
        }
        return super.onOptionsItemSelected(item);
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        //停止读线程
//        stopReadThread();
//        //停止文件测试
//        LogUtil.d("线程已销毁");
//        if (FILE_TEST) {
//            cancelLinks();
//        }
//        //关闭所有连接设备
//        closeAll();
//        //释放资源
//        WCHUARTManager.getInstance().close(this);
//    }

    /**
     * 系统是否支持USB Host功能
     *
     * @return true:系统支持USB Host false:系统不支持USB Host
     */
    public boolean UsbFeatureSupported() {
        boolean bool = this.getPackageManager().hasSystemFeature(
                "android.hardware.usb.host");
        return bool;
    }

    void initUI() {
//        deviceRecyclerVIew = findViewById(R.id.rvDevice);
        readBuffer = findViewById(R.id.tvReadData);

        start_btn = findViewById(R.id.btn_start);
        readCount = findViewById(R.id.tvReadCount);
        //初始化recyclerview
//        deviceRecyclerVIew.setNestedScrollingEnabled(false);
        deviceAdapter = new DeviceAdapter(this);

        epcList = new ArrayList<EPC>();

//        deviceRecyclerVIew.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        deviceAdapter.setEmptyView(LayoutInflater.from(this).inflate(R.layout.empty_view, deviceRecyclerVIew, false));
//        deviceRecyclerVIew.setAdapter(deviceAdapter);
        deviceAdapter.setActionListener(new DeviceAdapter.OnActionListener() {
            @Override
            public void onRemove(UsbDevice usbDevice) {
                removeReadDataDevice(usbDevice);
            }
        });

        readBuffer.setMovementMethod(ScrollingMovementMethod.getInstance());
//        clearRead.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                clearReadData();
//            }
//        });
        //监测USB插拔状态
        monitorUSBState();
        //动态申请权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 111);
        }
        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(start_btn.getText().equals("开始盘点")){
                    showLabnumberdialog();
                    start_btn.setText("停止盘点");
                }else{
                    start_btn.setText("开始盘点");
                    stopReading();
                    showUploaddialog();
                }

            }
        });

    }

    boolean setSerialParameter(){
        SerialBaudBean serialBaudBean = new SerialBaudBean();
        serialBaudBean.setBaud(115200);
        serialBaudBean.setData(8);
        serialBaudBean.setFlow(false);
        serialBaudBean.setStop(1);
        //serialBaudBean.setFlow(true);
        UsbDevice usbDevice = serialEntity.getUsbDevice();
        int serialNumber = serialEntity.getSerialNumber();
        //设置波特率 = 115200
        try {
            boolean b = WCHUARTManager.getInstance().setSerialParameter(serialEntity.getUsbDevice(), serialEntity.getSerialNumber(),
                    serialBaudBean.getBaud(), serialBaudBean.getData(), serialBaudBean.getStop(), serialBaudBean.getParity(),serialBaudBean.isFlow());
            return b;
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
        }
        //设置RTS=true
        try {
            boolean b=WCHUARTManager.getInstance().setRTS(usbDevice, serialNumber, true);
            if(!b){
                showToast("设置RTS失败");
            }
            //showToast("设置RTS"+(b?"成功":"失败"));
        } catch (Exception e) {
            showToast(e.getMessage());
        }
        //设置DTR=true
        try {
            boolean b=WCHUARTManager.getInstance().setDTR(usbDevice, serialNumber, true);
            if(!b){
                showToast("设置DTR失败");
            }
            //showToast("设置DTR"+(b?"成功":"失败"));
        } catch (Exception e) {
            showToast(e.getMessage());
        }
        //设置Break=false
        try {
            boolean b=WCHUARTManager.getInstance().setBreak(usbDevice, serialNumber, false);
            if(!b){
                showToast("设置Break失败");
            }
            //showToast("设置Break"+(b?"成功":"失败"));
        } catch (Exception e) {
            showToast(e.getMessage());
        }
        return false;
    }

//    void openGPIODialog() {
//        //simply,select first device
//        UsbDevice device = null;
//        Iterator<UsbDevice> iterator = devices.iterator();
//        while (iterator.hasNext()) {
//            device = iterator.next();
//        }
//        if (device != null) {
//            GPIODialog dialog = GPIODialog.newInstance(device);
//            dialog.setCancelable(false);
//            dialog.show(getSupportFragmentManager(), GPIODialog.class.getName());
//
//        }
//
//    }

    /**
     * 枚举当前所有符合要求的设备，显示设备列表
     */
    void enumDevice() {
        try {
            //枚举符合要求的设备
            ArrayList<UsbDevice> usbDeviceArrayList = WCHUARTManager.getInstance().enumDevice();
            if (usbDeviceArrayList.size() == 0) {
                showToast("no matched devices");
                return;
            }
            //显示设备列表dialog
            DeviceListDialog deviceListDialog = DeviceListDialog.newInstance(usbDeviceArrayList);
            deviceListDialog.setCancelable(false);
            deviceListDialog.show(getSupportFragmentManager(), DeviceListDialog.class.getName());
            deviceListDialog.setOnClickListener(new DeviceListDialog.OnClickListener() {
                @Override
                public void onClick(UsbDevice usbDevice) {
                    //选择了某一个设备打开
                    open(usbDevice);
                    serialEntity = new SerialEntity(usbDevice,0);
                    if(setSerialParameter()){
                        showToast("串口参数自动设置成功！");
                    }else{
                        showToast("串口参数自动设置失败！");
                    }
                    //startRead.setEnabled(true);
                }
            });
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
        }
    }

    /**
     * 从设备列表中打开某个设备
     *
     * @param usbDevice
     */
    void open(@NonNull UsbDevice usbDevice) {
        if (WCHUARTManager.getInstance().isConnected(usbDevice)) {
            showToast("当前设备已经打开");
            return;
        }
        try {
            boolean b = WCHUARTManager.getInstance().openDevice(usbDevice);
            if (b) {
                //打开成功
                //更新显示的ui
                update(usbDevice);
                //readThread.start();
                //初始化接收计数
                int serialCount = 0;
                try {
                    serialCount = WCHUARTManager.getInstance().getSerialCount(usbDevice);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                for (int i = 0; i < serialCount; i++) {
                    readCountMap.put(FormatUtil.getSerialKey(usbDevice, i), 0);
                }
                //将该设备添加至已打开设备列表,在读线程ReadThread中,将会读取该设备的每个串口数据
                addToReadDeviceSet(usbDevice);
                //用作文件对比测试,在打开每个设备时，对每个串口新建对应的保存数据的文件
//                if (FILE_TEST) {
//                    for (int i = 0; i < serialCount; i++) {
//                        linkSerialToFile(usbDevice, i);
//                    }
//                }
                registerModemStatusCallback(usbDevice);
                registerDataCallback(usbDevice);
            } else {
                showToast("打开失败");
            }
        } catch (ChipException e) {
            LogUtil.d(e.getMessage());
        } catch (NoPermissionException e) {
            //没有权限打开该设备
            //申请权限
            showToast("没有权限打开该设备");
            requestPermission(usbDevice);
        } catch (UartLibException e) {
            e.printStackTrace();
        }
    }

    /**
     * 申请读写权限
     *
     * @param usbDevice
     */
    private void requestPermission(@NonNull UsbDevice usbDevice) {
        try {
            WCHUARTManager.getInstance().requestPermission(this, usbDevice);
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
        }
    }

    /**
     * 监测USB的状态
     */
    private void monitorUSBState() {
        WCHUARTManager.getInstance().setUsbStateListener(new IUsbStateChange() {
            @Override
            public void usbDeviceDetach(UsbDevice device) {
                //设备移除
                removeReadDataDevice(device);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //从界面上移除
                        if (deviceAdapter != null) {
                            deviceAdapter.removeDevice(device);
                        }
                    }
                });

            }

            @Override
            public void usbDeviceAttach(UsbDevice device) {
                //设备插入
            }

            @Override
            public void usbDevicePermission(UsbDevice device, boolean result) {
                //请求打开设备权限结果
            }
        });
    }

    /**
     * //recyclerView更新UI
     *
     * @param usbDevice
     */
    void update(UsbDevice usbDevice) {
        //根据vid/pid获取芯片类型
        ChipType2 chipType = null;
        try {
            chipType = WCHUARTManager.getInstance().getChipType(usbDevice);
            //获取芯片串口数目,为负则代表出错
            int serialCount = WCHUARTManager.getInstance().getSerialCount(usbDevice);
            //构建recyclerView所绑定的数据,添加设备
//            ArrayList<SerialEntity> serialEntities = new ArrayList<>();
//            for (int i = 0; i < serialCount; i++) {
//                SerialEntity serialEntity = new SerialEntity(usbDevice, i);
//                serialEntities.add(serialEntity);
//            }
            SerialEntity serialEntity1 = new SerialEntity(usbDevice,0);
            DeviceEntity deviceEntity = new DeviceEntity(usbDevice, chipType.getDescription(), serialEntity1);
            if (deviceAdapter.hasExist(deviceEntity)) {
                //已经显示
                showToast("该设备已经存在");
            } else {
                deviceAdapter.addDevice(deviceEntity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 关闭所有设备
     */
    void closeAll() {
        ArrayList<UsbDevice> usbDeviceArrayList = null;
        try {
            usbDeviceArrayList = WCHUARTManager.getInstance().enumDevice();
            for (UsbDevice usbDevice : usbDeviceArrayList) {
                if (WCHUARTManager.getInstance().isConnected(usbDevice)) {
                    WCHUARTManager.getInstance().disconnect(usbDevice);
                }
            }
            onDestroy();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void addToReadDeviceSet(@NonNull UsbDevice usbDevice) {
        synchronized (devices) {
            devices.add(usbDevice);
        }

    }

    private void removeReadDataDevice(@NonNull UsbDevice usbDevice) {
        synchronized (devices) {
            devices.remove(usbDevice);
        }
    }

    private void registerModemStatusCallback(UsbDevice usbDevice) {
        try {
            WCHUARTManager.getInstance().registerModemStatusCallback(usbDevice, new IModemStatus() {
                @Override
                public void onStatusChanged(int serialNumber, boolean isDCDRaised, boolean isDSRRaised, boolean isCTSRaised, boolean isRINGRaised) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceAdapter.updateDeviceModemStatus(usbDevice, serialNumber, isDCDRaised, isDSRRaised, isCTSRaised, isRINGRaised);
                        }
                    });
                }

                @Override
                public void onOverrunError(int serialNumber) {
                    try {
                        int count = WCHUARTManager.getInstance().querySerialErrorCount(usbDevice, serialNumber, SerialErrorType.OVERRUN);
                        LogUtil.d("overrun error: " + count);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceAdapter.updateDeviceModemErrorStatus(usbDevice, new ModemErrorEntity(serialNumber, ModemErrorEntity.ErrorType.OVERRUN));

                        }
                    });

                }

                @Override
                public void onParityError(int serialNumber) {
                    try {
                        int count = WCHUARTManager.getInstance().querySerialErrorCount(usbDevice, serialNumber, SerialErrorType.PARITY);
                        LogUtil.d("parity error: " + count);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LogUtil.d("parity error!");
                            deviceAdapter.updateDeviceModemErrorStatus(usbDevice, new ModemErrorEntity(serialNumber, ModemErrorEntity.ErrorType.PARITY));

                        }
                    });
                }

                @Override
                public void onFrameError(int serialNumber) {
                    try {
                        int count = WCHUARTManager.getInstance().querySerialErrorCount(usbDevice, serialNumber, SerialErrorType.FRAME);
                        LogUtil.d("frame error: " + count);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LogUtil.d("frame error!");
                            deviceAdapter.updateDeviceModemErrorStatus(usbDevice, new ModemErrorEntity(serialNumber, ModemErrorEntity.ErrorType.FRAME));

                        }
                    });
                }

            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerDataCallback(UsbDevice usbDevice) {
        try {
            WCHUARTManager.getInstance().registerDataCallback(usbDevice, new IDataCallback() {
                @Override
                public void onData(int serialNumber, byte[] buffers, int length) {
                    //LogUtil.d(String.format(Locale.getDefault(),"serial %d receive data %d:%s", serialNumber,length+1, FormatUtil.bytesToHexString(buffers, length+1)));
                    //1.注意回调的执行线程与调用回调方法的线程属于同一线程
                    //2.此处所在的线程将是线程池中多个端点的读取线程，可打印线程id查看
                    //3.buffer是底层数组，如果此处将其传给其他线程使用，例如通过runOnUiThread显示数据在界面上,
                    //涉及到线程切换需要一定时间，buffer可能被读到的新数据覆盖，可以新建一个临时数组保存数据

                    byte[] bufer = new byte[length];
                    LogUtil.d("bufferlength:"+length);
                    System.arraycopy(buffers, 0, bufer, 0, bufer.length);
                    //LogUtil.d("原始数据 ："+FormatUtil.bytesToHexString(bufer));
                    List<byte[]>  result = Trans.getEpc(bufer,length);
                    if (result.size() == 0){
                        LogUtil.d("没有读到标签");
                    }else {
                        for(byte[] epc:result){//增强for循环遍历set
                            if(epc.length == 4 || epc.length ==8){//epc为半截
                                if(epcflag == 0){//当前还没有前半截数据
                                    EPChalf = FormatUtil.bytesToHexString(epc);
                                    LogUtil.d("epc_4/8:"+EPChalf);
                                    epcflag = 1;
                                }else {
                                    EPChalf=EPChalf.concat(FormatUtil.bytesToHexString(epc));
                                    LogUtil.d("epc_4/8:"+EPChalf);
                                    LogUtil.d("EPChalf:"+EPChalf);
                                    epcflag = 0 ;
                                    boolean re = addToList(usbDevice,epcList,EPChalf);//判断是否是重复标签
                                    EPChalf = "";
                                }
                            }else if(epc.length == 12){//epc为完整的12个字节
                                boolean re = addToList(usbDevice,epcList,FormatUtil.bytesToHexString(epc));
                                LogUtil.d("12EPC:"+FormatUtil.bytesToHexString(epc));
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
        }
    }

    public  int updateReadData(@NonNull UsbDevice usbDevice, int serialNumber, byte[] buffer, int length) {
        if (buffer == null) {
            return -1;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String result = "";
                if (readBuffer.getText().toString().length() >= 1500) {
                    readBuffer.setText("");
                    readBuffer.scrollTo(0, 0);
                }
                result = FormatUtil.bytesToHexString(buffer, length);
                readBuffer.append("读到的标签号："+result + "\r\n");
                LogUtil.d("读取到的数据" + result);

                int offset = readBuffer.getLineCount() * readBuffer.getLineHeight();
                int height = readBuffer.getHeight();
                if (offset > height) {
                    //USBLog.d("scroll: "+(offset - usbReadValue.getHeight() + usbReadValue.getLineHeight()));
                    readBuffer.scrollTo(0, offset - readBuffer.getHeight() + readBuffer.getLineHeight());
                }
            }
        });
        return 0;
    }


    private void clearReadData() {
        readBuffer.scrollTo(0, 0);
        readBuffer.setText("");
        for (String s : readCountMap.keySet()) {
            readCountMap.put(s, 0);
        }
    }

    private void showToast(String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //ToastUtil.create(context,message).show();
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    ///////////////////////////////////////将数据保存至文件,与发送文件对比测试////////////////////////////////////////////////////

    //该Map的key是每个设备的串口，value是其对应的保存数据的文件的fileStream
    private HashMap<String, FileOutputStream> fileOutputStreamMap = new HashMap<>();

    public void getRequest(View view) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(1000, TimeUnit.MILLISECONDS)
                .build();
    }

    public void postFile() {
        Gson gson1 = new Gson();
        String jsonstr = gson1.toJson(jsonEntitylist);
        System.out.println(jsonstr);
        LogUtil.d(jsonstr);
        String url = "http://192.168.1.195:9102/post/comment";
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(1000,TimeUnit.MILLISECONDS).build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody =RequestBody.create(jsonstr,mediaType);
        Request request = new Request.Builder().url(url).post(requestBody).build();
        Call task = httpClient.newCall(request);
        task.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.d(TAG, "上传失败--> " + e.toString());
                Looper.prepare();
                showUploadFailDialog();
                Looper.loop();

            }
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                int code = response.code();
                Log.d(TAG, "code == " + code);
                if (code == HttpURLConnection.HTTP_OK) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        String result = body.string();
                        Log.d(TAG, "result ---> " + result);
                    }
                }
                if (code == 200){
                    ToastUtil.create(MainActivity.this,"上传成功！");
                }
            }
        });
    }
    public void post() {
        Thread uploadthread = new Thread(new Runnable() {
            @Override
            public void run() {
                postFile();
            }
        });
        uploadthread.start();
    }

    //将读取的EPC添加到LISTVIEW
    private boolean addToList(UsbDevice usbDevice,final List<EPC> list, String epc){
        boolean flag = true;
        //第一次读入数据,列表为空
        if(list.isEmpty()){
            EPC epcTag = new EPC();
            epcTag.setEpc(epc);
            epcTag.setCount(1);
            list.add(epcTag);
            JsonEntity jsonEntity = new JsonEntity(selectedtLab,epc);
            jsonEntitylist.add(jsonEntity);
            updateReadData(usbDevice, 0,
                    FormatUtil.hexStringToBytes(epc), 12);
        }else{
            for(int i = 0; i < list.size(); i++){
                EPC mEPC = list.get(i);
                //list中有此epc
                if(epc.equals(mEPC.getEpc())){
                    mEPC.setCount(mEPC.getCount() + 1);
                    list.set(i, mEPC);
                    flag = false;
                    break;
                }else if(i == (list.size() - 1)){//list最后一个
                    //list中没有此epc
                    EPC newEPC = new EPC();
                    newEPC.setEpc(epc);
                    newEPC.setCount(1);
                    list.add(newEPC);
                    JsonEntity jsonEntity = new JsonEntity(selectedtLab,epc);
                    jsonEntitylist.add(jsonEntity);
                    updateReadData(usbDevice, 0,
                            FormatUtil.hexStringToBytes(epc), 12);
                }
            }
        }
        return flag;

                //将数据添加到ListView
//                listMap = new ArrayList<Map<String,Object>>();
//                int idcount = 1;
//                for(EPC epcdata:list){
//                    Map<String, Object> map = new HashMap<String, Object>();
//                    map.put("ID", idcount);
//                    map.put("EPC", epcdata.getEpc());
//                    map.put("COUNT", epcdata.getCount());
//                    idcount++;
//                    listMap.add(map);
//                }
//                listViewData.setAdapter(new SimpleAdapter(MainActivity.this,
//                        listMap, R.layout.listview_item,
//                        new String[]{"ID", "EPC", "COUNT"},
//                        new int[]{R.id.textView_id, R.id.textView_epc, R.id.textView_count}));


    }

    //向串口发送数据函数，将要发送的数据作为参数string传进来
    public  int sendData(UsbDevice usbDevice,int serialNumber,String string){
        byte[] data = FormatUtil.hexStringToBytes(string);
        //LogUtil.d("senddata:"+FormatUtil.bytesToHexString(data, data.length));
        try {
            int write = WCHUARTManager.getInstance().writeData(usbDevice, serialNumber, data, data.length,2000);
            return write;
        } catch (Exception e) {
            LogUtil.d(e.getMessage());
            return -2;
        }
    }
    //点击“开始盘点”的事件：向串口发送多次轮询指令
    public void startReading(){
        String string = "AA0027000322FFFF4ADD";
        int result = sendData(serialEntity.getUsbDevice(), 0,string);
        if(result == -2){
            showToast("发送失败!请重试~");
        }else{
            showToast("发送成功！");
        }
    }
    //点击”停止盘点“事件，向串口发送停止轮询指令
    public void stopReading(){
        String string = "AA0028000028DD";
        int result = sendData(serialEntity.getUsbDevice(), serialEntity.getSerialNumber(),string);
        if(result == -2){
            showToast("发送失败!请重试~");
        }else{
            showToast("发送成功！");
        }
    }

    public void showUploaddialog(){
        AlertDialog dialog=new AlertDialog.Builder(this)
               // .setIcon(R.drawable.hmbb)//设置图标
                .setTitle("上传")//设置标题
                .setMessage("是否将刚刚盘点的信息上传？")//设置要显示的内容
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();//销毁对话框
                    }
                })
                .setPositiveButton("确认上传", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "正在上传请 等待片刻~",
                                Toast.LENGTH_SHORT).show();
                        post();
                        dialog.dismiss();//销毁对话框
                    }
                }).create();//create（）方法创建对话框
        dialog.show();//显示对话框
    }
    public void showLabnumberdialog(){
        //列出实验室编号
        final String[] labnumber = getResources().getStringArray(R.array.labnumber);
        AlertDialog dialog3 = new AlertDialog.Builder(this)
                //.setIcon(R.drawable.hmbb)//设置标题的图片
                .setTitle("选择你盘点的实验室号")//设置对话框的标题
                //第一个参数:设置单选的资源;第二个参数:设置默认选中哪几项
                .setSingleChoiceItems(labnumber, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        selectedtLab = labnumber[i];
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        startReading();
                        LogUtil.d("实验室号："+selectedtLab);
                    }

                }).create();
        dialog3.show();
    }
    public void showUploadFailDialog(){//上传失败对话框
        AlertDialog dialog=new AlertDialog.Builder(this)
                // .setIcon(R.drawable.hmbb)//设置图标
                .setTitle("上传失败！")//设置标题
                .setMessage("是否尝试重新上传？")//设置要显示的内容
                .setNegativeButton("否", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();//销毁对话框
                    }
                })
                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "正在上传请 等待片刻~",
                                Toast.LENGTH_SHORT).show();
                        postFile();
                        dialog.dismiss();//销毁对话框
                    }
                }).create();//create（）方法创建对话框
        dialog.show();//显示对话框
    }

}