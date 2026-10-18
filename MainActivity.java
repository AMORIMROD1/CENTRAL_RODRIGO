package app.lovable.centralrodrigo;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AlarmePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
