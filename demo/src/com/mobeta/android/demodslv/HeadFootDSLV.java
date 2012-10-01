package com.mobeta.android.demodslv;

import android.app.ListActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.mobeta.android.dslv.DragSortListView;

import java.util.ArrayList;
import java.util.Arrays;

public class HeadFootDSLV extends ListActivity {

    private ArrayAdapter<String> adapter;

    private String[] array;
    private ArrayList<String> list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dslv_main);

        DragSortListView lv = (DragSortListView) getListView();

        // Add header view
        TextView tv = (TextView) getLayoutInflater().inflate(R.layout.list_headfoot, null);
        tv.setText("Header View");
        lv.addHeaderView(tv);

        // Add footer view
        tv = (TextView) getLayoutInflater().inflate(R.layout.list_headfoot, null);
        tv.setText("Footer View");
        lv.addFooterView(tv);

        lv.setDropListener(onDrop);
        lv.setRemoveListener(onRemove);

        array = getResources().getStringArray(R.array.jazz_artist_names);
        list = new ArrayList<String>(Arrays.asList(array));

        adapter = new ArrayAdapter<String>(this, R.layout.list_item1, R.id.text1, list);
        setListAdapter(adapter);
    }

    private DragSortListView.DropListener onDrop = new DragSortListView.DropListener() {
        @Override
        public void drop(int from, int to) {
            String item = adapter.getItem(from);

            adapter.remove(item);
            adapter.insert(item, to);
        }
    };

    private DragSortListView.RemoveListener onRemove = new DragSortListView.RemoveListener() {
        @Override
        public void remove(int which) {
            adapter.remove(adapter.getItem(which));
        }
    };
}
