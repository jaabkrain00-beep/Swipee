package com.swipeclean.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PhotoManagerPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
