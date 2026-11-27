package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.CommunitySettingsUi;
import org.telegram.ui.Components.LayoutHelper;

/** Explicit review and restoration of locally captured, plain-text drafts. */
public class CommunityDraftHistoryActivity extends BaseFragment {
    private LinearLayout entries;
    private boolean alive=true;
    private long owner;
    private int generation;
    private boolean valid(){return alive&&owner!=0&&owner==CommunityDraftHistory.owner(currentAccount);}
    private String text(int key){return LocaleController.getString(key);}
    @Override public View createView(Context context){
        owner=CommunityDraftHistory.owner(currentAccount);
        actionBar.setTitle(text(R.string.CommunityDraftHistory));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);fragmentView=scroll;
        label(body,text(R.string.CommunityDraftHistoryInfo));
        CheckBox enabled=new CheckBox(context);enabled.setText(text(R.string.CommunityDraftHistoryEnable));enabled.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));enabled.setMinHeight(AndroidUtilities.dp(48));enabled.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));enabled.setChecked(CommunityDraftHistory.enabled(currentAccount));body.addView(enabled);
        enabled.setOnCheckedChangeListener((button,value)->{if(valid())CommunityDraftHistory.setEnabled(currentAccount,value,this::refresh);});
        button(body,R.string.CommunityDraftHistoryClear,()->{if(valid())showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.CommunityDraftHistoryClear)).setMessage(text(R.string.CommunityDraftHistoryClearInfo)).setPositiveButton(text(R.string.Delete),(dialog,which)->{if(valid())CommunityDraftHistory.clear(currentAccount,this::refresh);}).setNegativeButton(text(R.string.Cancel),null).create());});
        button(body,R.string.Refresh,this::refresh);
        entries=new LinearLayout(context);entries.setOrientation(LinearLayout.VERTICAL);body.addView(entries);refresh();return scroll;
    }
    private TextView label(LinearLayout body,String value){TextView view=new TextView(body.getContext());view.setText(value);view.setTextSize(15);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));body.addView(view,LayoutHelper.createLinear(-1,-2));return view;}
    private void button(LinearLayout body,int key,Runnable click){TextView view=label(body,text(key));CommunitySettingsUi.button(view);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));view.setOnClickListener(v->click.run());}
    private String peer(CommunityDraftHistoryModel.Entry entry){
        MessagesController mc=MessagesController.getInstance(currentAccount);String name;
        if(entry.dialogId>0){TLRPC.User user=mc.getUser(entry.dialogId);name=user==null?text(R.string.CommunityDraftHistoryUnknown):UserObject.getUserName(user);}
        else {TLRPC.Chat chat=mc.getChat(-entry.dialogId);name=chat==null?text(R.string.CommunityDraftHistoryUnknown):chat.title;}
        if(entry.threadId!=0)name+=" "+LocaleController.formatString("CommunityDraftHistoryThread",R.string.CommunityDraftHistoryThread,entry.threadId);
        return name;
    }
    private void refresh(){
        if(entries==null||!valid())return;final int request=++generation;
        CommunityDraftHistory.snapshot(currentAccount,snapshot->{if(!valid()||request!=generation)return;entries.removeAllViews();
            if(snapshot.isEmpty()){label(entries,text(R.string.CommunityDraftHistoryEmpty));return;}
            java.text.DateFormat dates=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,LocaleController.getInstance().getCurrentLocale());
            for(CommunityDraftHistoryModel.Entry entry:snapshot){
                label(entries,peer(entry)+" · "+dates.format(new java.util.Date(entry.savedAt))+"\n"+(entry.text.length()>180?entry.text.substring(0,180)+"…":entry.text));
                button(entries,R.string.CommunityDraftHistoryView,()->{if(valid())showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(peer(entry)).setMessage(entry.text).setPositiveButton(text(R.string.OK),null).create());});
                button(entries,R.string.CommunityDraftHistoryRestore,()->confirmRestore(entry));
                button(entries,R.string.Delete,()->{if(valid())showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.Delete)).setMessage(text(R.string.CommunityDraftHistoryDeleteInfo)).setPositiveButton(text(R.string.Delete),(dialog,which)->{if(valid())CommunityDraftHistory.remove(currentAccount,entry.id,this::refresh);}).setNegativeButton(text(R.string.Cancel),null).create());});
            }
        });
    }
    private void confirmRestore(CommunityDraftHistoryModel.Entry entry){
        if(!valid())return;
        TLRPC.DraftMessage expected=MediaDataController.getInstance(currentAccount).getDraft(entry.dialogId,entry.threadId);
        String message=text(R.string.CommunityDraftHistoryRestoreInfo);
        if(expected!=null&&(!(expected instanceof TLRPC.TL_draftMessageEmpty)))message+="\n\n"+text(R.string.CommunityDraftHistoryExisting);
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.CommunityDraftHistoryRestore)).setMessage(message).setPositiveButton(text(R.string.CommunityDraftHistoryRestore),(dialog,which)->{
            if(valid())CommunityDraftHistory.restore(currentAccount,entry,expected,ok->{if(valid()){Toast.makeText(getParentActivity(),text(ok?R.string.CommunityDraftHistoryRestored:R.string.CommunityDraftHistoryUnavailable),Toast.LENGTH_LONG).show();refresh();}});
        }).setNegativeButton(text(R.string.Cancel),null).create());
    }
    @Override public void onResume(){super.onResume();refresh();}
    @Override public void onFragmentDestroy(){alive=false;generation++;super.onFragmentDestroy();}
}
