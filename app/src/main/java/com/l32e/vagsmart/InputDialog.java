package com.l32e.vagsmart;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

/**
 * Created by frank.fan on 2/5/2018.
 */

public class InputDialog extends AppCompatDialogFragment {
    private EditText editTextUsername;
    private EditText editTextSession;
    private RadioGroup radioGroupActivitySelect;
    private Boolean isGaming;
    private InputDialogListenser listenser;
    private int selectedId;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.layout_dialog, null);
        editTextUsername = (EditText)view.findViewById(R.id.edit_username);
        editTextSession = (EditText)view.findViewById(R.id.edit_session);
        radioGroupActivitySelect = (RadioGroup) view.findViewById(R.id.radioGroupSelectActivity);

        builder.setView(view)
                .setTitle("")
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    String username = editTextUsername.getText().toString();
                    String session = editTextSession.getText().toString();
                    //int selectedId = radioGroupActivitySelect.getCheckedRadioButtonId();
                    if (radioGroupActivitySelect.getCheckedRadioButtonId()==R.id.onlineGame){
                        selectedId = 0;
                    }
                    else if (radioGroupActivitySelect.getCheckedRadioButtonId()==R.id.engineeringLog){
                        selectedId = 1;
                    }else
                        selectedId = 2;
                    listenser.dialogReturnInfo(username,session,selectedId);
                    }
                });

        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            listenser = (InputDialogListenser) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()+ "need to implement InputDialogListener");
        }
    }

    public interface InputDialogListenser{
        void dialogReturnInfo(String username, String session, int selectedId);
    }
}
