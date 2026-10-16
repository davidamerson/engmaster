package com.kidsvideo.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ApiSetupActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);Prefs p=new Prefs(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,28),Ui.dp(this,28),Ui.dp(this,28),Ui.dp(this,28));root.setBackgroundColor(Color.rgb(15,15,15));TextView title=new TextView(this);title.setText("Parent setup");title.setTextColor(Color.WHITE);title.setTextSize(28);title.setTypeface(null,1);root.addView(title);TextView info=new TextView(this);info.setText("Enter the private YouTube Data API key created in Google Cloud. It stays only on this device. Set a parent PIN for settings.");info.setTextColor(Color.LTGRAY);info.setTextSize(16);info.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,20));root.addView(info);EditText key=new EditText(this);key.setHint("YouTube Data API key");key.setText(p.apiKey());key.setTextColor(Color.WHITE);key.setHintTextColor(Color.GRAY);key.setSingleLine(true);root.addView(key,new LinearLayout.LayoutParams(-1,-2));EditText pin=new EditText(this);pin.setHint("Parent PIN (4+ digits)");pin.setText(p.parentPin());pin.setTextColor(Color.WHITE);pin.setHintTextColor(Color.GRAY);pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setSingleLine(true);root.addView(pin,new LinearLayout.LayoutParams(-1,-2));Button save=new Button(this);save.setText("Save and continue");save.setAllCaps(false);save.setFocusable(true);save.setOnClickListener(v->{String k=key.getText().toString().trim(),x=pin.getText().toString().trim();if(k.length()<20){Toast.makeText(this,"Please enter a valid API key.",Toast.LENGTH_LONG).show();return;}if(x.length()<4){Toast.makeText(this,"Please use a PIN with at least 4 digits.",Toast.LENGTH_LONG).show();return;}p.setApiKey(k);p.setParentPin(x);p.clearCache();setResult(RESULT_OK);finish();});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=Ui.dp(this,22);root.addView(save,sp);setContentView(root);}
}
