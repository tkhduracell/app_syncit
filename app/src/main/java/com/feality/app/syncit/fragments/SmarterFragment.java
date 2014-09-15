package com.feality.app.syncit.fragments;

import android.app.Fragment;
import android.view.View;

/**
 * Created by Filip on 2014-09-10.
 */
public class SmarterFragment extends Fragment {

    public <T> T get(View v, int id, Class<T> clz){
        return clz.cast(v.findViewById(id));
    }

    public <T> T get(int id, Class<T> clz){
        final View view = getView();
        if (view != null) {
            return clz.cast(view.findViewById(id));
        }
        throw new RuntimeException("No layout to get view from");
    }
}
