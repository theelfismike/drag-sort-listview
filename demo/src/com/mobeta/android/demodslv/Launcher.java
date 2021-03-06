package com.mobeta.android.demodslv;

import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

public class Launcher extends ListActivity {

    //private ArrayAdapter<ActivityInfo> adapter;
    private MyAdapter adapter;

    private ArrayList<ActivityInfo> mActivities = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launcher);

        try {
            PackageInfo pi = getPackageManager().getPackageInfo("com.mobeta.android.demodslv",
                    PackageManager.GET_ACTIVITIES);

            mActivities = new ArrayList<ActivityInfo>(Arrays.asList(pi.activities));
        } catch(PackageManager.NameNotFoundException e) {
            // Do nothing. Adapter will be empty.
        }

        adapter = new MyAdapter();
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = new Intent();

        if(position > 0) {
            intent.setClassName(this, mActivities.get(position).name);
            startActivity(intent);
        }

    }

    private class MyAdapter extends ArrayAdapter<ActivityInfo> {
        MyAdapter() {
            super(Launcher.this, R.layout.launcher_item, R.id.text, mActivities);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView v = (TextView) super.getView(position, convertView, parent);
            v.setText(mActivities.get(position).loadLabel(getPackageManager()));
            return v;
        }
    }
}
