/*
 * Developer
 * Sandeep Dillerao (India)
 * sandydillerao@gmail.com
 * +91 8483094292
 * */
package org.apache.cordova.OTPAutoVerification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class echoes a string called from JavaScript.
 */
public class OTPAutoVerification extends CordovaPlugin {

    private IntentFilter filter;
    private static final String TAG = OTPAutoVerification.class.getSimpleName();

    public static int OTP_LENGTH = 0;
    public JSONArray options;
    public CallbackContext callbackContext;
    private Context mContext;
    @Override
    public boolean execute(String action, JSONArray options, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("startOTPListener")) {
            Log.i(TAG, options.toString());
            this.options = options;
            this.callbackContext = callbackContext;
            this.mContext = this.cordova.getActivity().getApplicationContext();
            SMSListener.bindListener(new Common.OTPListener() {
                @Override
                public void onOTPReceived(String otp) {
                    Log.e(TAG, "OTP received: " + otp);
                    stopOTPListener();
                    callbackContext.success(otp);
                }

                @Override
                public void onOTPTimeOut() {
                    Log.e(TAG, "OTP Timeout: ");
                    stopOTPListener();
                    callbackContext.error("TIMEOUT");
                }
            });
            startOTPListener(options, callbackContext);

            return true;
        }else if (action.equals("stopOTPListener")) {
            stopOTPListener();
            return true;
        }
        return false;
    }

    private void startOTPListener(JSONArray options, final CallbackContext callbackContext) {
        /* take init parameter from JS call */
        try {
            OTP_LENGTH = options.getJSONObject(0).getInt("length");

        } catch (JSONException e) {
            e.printStackTrace();
        }
        startSMSListener();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SmsRetriever.SMS_RETRIEVED_ACTION);
        cordova.getActivity().registerReceiver(new SMSListener(),filter); // start BroadcastReceiver 
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT); // Cordova plugin 
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        Log.d("SMS pluginResult", pluginResult.toString());
    }

    private void stopOTPListener(){
        Log.d("OTPAutoVerification", "stopOTPListener");
        SMSListener.unbindListener();
    }

    private void startSMSListener() {
        // Get an instance of SmsRetrieverClient, used to start listening for a matching
        // SMS message.
        Log.d(TAG,"startSMSListener@ SmsRetrieverClient");
        SmsRetrieverClient client = SmsRetriever.getClient(mContext);

        // Starts SmsRetriever, which waits for ONE matching SMS message until timeout
        // (5 minutes). The matching SMS message will be sent via a Broadcast Intent with
        // action SmsRetriever#SMS_RETRIEVED_ACTION.
        Task<Void> task = client.startSmsRetriever();

        // Listen for success/failure of the start Task. If in a background thread, this
        // can be made blocking using Tasks.await(task, [timeout]);
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                // Successfully started retriever, expect broadcast intent
                // ...
                Log.d("smsListener", "SUCCESS");
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Failed to start retriever, inspect Exception for more details
                // ...
                Log.d("smsListener", "FAILED" + e.toString());
            }
        });
    }


    /*
     * Interface for OTP sms Listener
     * */
    public interface Common {
        interface OTPListener {
            void onOTPReceived(String otp);
            void onOTPTimeOut();
        }
    }

    /*
     * broadcast listener to listen for MESSAGE
     * @return originalMessage and Sender
     * onOTPReceived(smsMessage.getDisplayMessageBody(), senderAddress);
     * */
    public static class SMSListener extends BroadcastReceiver {

        private static OTPAutoVerification.Common.OTPListener mListener; // this listener will do the magic of throwing the extracted OTP to all the bound views.

        @Override
        public void onReceive(Context context, Intent intent) {

            // this function is trigged when each time a new SMS is received on device.
            Log.d(TAG,"onReceive" + String.valueOf(SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())));
            if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                switch(status.getStatusCode()) {
                    case CommonStatusCodes.SUCCESS:
                        // Get SMS message contents
                        // Toast.makeText(context, "Don't panik but your time is up!!!!.",
                        // Toast.LENGTH_LONG).show();
                        String smsMessage = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                        Log.d(TAG, "-------SMSListener.onReceive@SUCCESS Retrieved sms code: " + smsMessage);
                        // Extract one-time code from the message and complete verification
                        // by sending the code back to your server.
                        if(mListener!=null){
                            Pattern pattern = Pattern.compile("(\\d{"+OTP_LENGTH+"})");
                            Matcher matcher = pattern.matcher(smsMessage);
                            String otp = "";
                            if (matcher.find()) {
                                otp = matcher.group(1);  // x digit number
                            }
                            Log.d(TAG,"-------SMSListener.onReceive@SUCCESS  mListener " + otp.toString());
                            mListener.onOTPReceived(otp);
                        }
                        mListener.onOTPReceived(otp);
                        break;
                    case CommonStatusCodes.TIMEOUT:
                        // Waiting for SMS timed out (5 minutes)
                        // Handle the error ...
                        if(mListener!=null){
                            mListener.onOTPTimeOut();
                        }
                        Log.d(TAG,"-------SMSListener.onReceive@TIMEOUT  failed ");
                        break;
                }
            }
        }

        public static void bindListener(OTPAutoVerification.Common.OTPListener listener) {
            mListener = listener;
        }

        public static void unbindListener() {
            mListener = null;
        }
    }
}
