package io.skygear.skygear.eventtracking.example;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.skygear.skygear.Configuration;
import io.skygear.skygear.Container;
import io.skygear.skygear.eventtracking.SkygearTracker;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Container mContainer;
    private SkygearTracker mTracker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration config = new Configuration.Builder().endPoint("http://192.168.1.127:3000/").apiKey("et").build();
        mContainer = new Container(this, config);
        mTracker = new SkygearTracker(mContainer);

        TextView textView = new TextView(this);
        textView.setText("Hello");
        textView.setClickable(true);
        textView.setOnClickListener(this);
        this.setContentView(textView);
    }

    @Override
    public void onClick(View v) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("some_custom_string_attribute", UUID.randomUUID().toString());
        mTracker.track("Click hello", attributes);
    }
}
