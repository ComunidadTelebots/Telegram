package org.telegram.ui.Components;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.web.OfflineArticleStore;
import org.telegram.ui.web.ReadingNotePolicy;

/** Plain text only; associated with the article URL, never with a paragraph or a cloud message. */
public final class ReadingNotesDialog {
    private ReadingNotesDialog() { }
    public static void show(Activity activity,int account,String url,Utilities.Callback<AlertDialog> display) {
        OfflineArticleStore.readNote(account,url,note->{
            if(activity==null||activity.isFinishing()||activity.isDestroyed())return;
            if(note==null){Toast.makeText(activity,LocaleController.getString(R.string.ReadingNotesFailed),Toast.LENGTH_LONG).show();return;}
            LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(8),AndroidUtilities.dp(20),0);
            TextView info=new TextView(activity);info.setText(LocaleController.getString(R.string.ReadingNotesInfo));info.setTextSize(14);info.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));content.addView(info,LayoutHelper.createLinear(-1,-2));
            EditText editor=new EditText(activity);editor.setTextSize(16);editor.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));editor.setHint(LocaleController.getString(R.string.ReadingNotesHint));
            editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            editor.setGravity(Gravity.TOP);editor.setMinLines(4);editor.setMaxLines(8);editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ReadingNotePolicy.MAX_CHARACTERS)});editor.setText(note);content.addView(editor,LayoutHelper.createLinear(-1,-2));
            final AlertDialog[] dialog=new AlertDialog[1];
            Utilities.Callback<String> save=text->{
                if(!ReadingNotePolicy.accepts(text)){editor.setError(LocaleController.getString(R.string.ReadingNotesLimit));return;}
                dialog[0].getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                dialog[0].getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
                OfflineArticleStore.saveNote(account,url,text,ok->{
                    if(activity.isFinishing()||activity.isDestroyed()||!dialog[0].isShowing())return;
                    if(ok)dialog[0].dismiss();
                    else {dialog[0].getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);dialog[0].getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(true);editor.setError(LocaleController.getString(R.string.ReadingNotesFailed));}
                });
            };
            dialog[0]=new AlertDialog.Builder(activity).setTitle(LocaleController.getString(R.string.ReadingNotesTitle)).setView(content)
                    .setPositiveButton(LocaleController.getString(R.string.Save),(d,w)->save.run(editor.getText().toString()))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel),(d,w)->d.dismiss())
                    .setNeutralButton(LocaleController.getString(R.string.Delete),(d,w)->save.run("")).create();
            dialog[0].setDismissDialogByButtons(false);
            display.run(dialog[0]);
        });
    }
}
