package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.CommunitySettingsUi;
import org.telegram.ui.Components.LayoutHelper;
import java.util.ArrayList;
import java.util.List;

/** Manual follow-up references. Opening an original is an explicit user action. */
public final class CommunityPendingRepliesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private long owner;
    private boolean alive=true,busy;
    private int generation,filter,visible=20;
    private LinearLayout results;
    private TextView status,more;
    private List<PendingReplyData.Entry> entries=new ArrayList<>();
    private String text(int id){return LocaleController.getString(id);}
    private String[] states(){return new String[]{text(R.string.CommunityPendingTodo),text(R.string.CommunityPendingWaiting),text(R.string.CommunityPendingResolved)};}
    @Override public boolean onFragmentCreate(){if(!super.onFragmentCreate())return false;NotificationCenter.getInstance(currentAccount).addObserver(this,NotificationCenter.appDidLogout);return true;}
    @Override public void didReceivedNotification(int id,int account,Object... args){if(id==NotificationCenter.appDidLogout&&account==currentAccount){generation++;entries.clear();if(results!=null)results.removeAllViews();finishFragment();}}
    @Override public View createView(Context context){
        if(owner==0)owner=UserConfig.getInstance(currentAccount).getClientUserId();
        actionBar.setTitle(text(R.string.CommunityPendingReplies));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);fragmentView=scroll;
        row(body,text(R.string.CommunityPendingRepliesInfo),null);
        Spinner filters=new Spinner(context);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,states()){
            private View themed(View view){
                if(view instanceof TextView){TextView label=(TextView)view;label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));label.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));label.setTextSize(16);label.setSingleLine(false);label.setMinHeight(AndroidUtilities.dp(48));}
                return view;
            }
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){return themed(super.getView(position,recycled,parent));}
            @Override public View getDropDownView(int position,View recycled,android.view.ViewGroup parent){return themed(super.getDropDownView(position,recycled,parent));}
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filters.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite)));
        filters.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)));
        filters.setAdapter(adapter);filters.setMinimumHeight(AndroidUtilities.dp(48));filters.setContentDescription(text(R.string.CommunityPendingStatus));filters.setSelection(filter);body.addView(filters,LayoutHelper.createLinear(-1,-2));
        filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){filter=position;visible=20;render();}public void onNothingSelected(AdapterView<?> p){}});
        status=row(body,"",null);row(body,text(R.string.Refresh),this::refresh);
        results=new LinearLayout(context);results.setOrientation(LinearLayout.VERTICAL);body.addView(results,LayoutHelper.createLinear(-1,-2));
        more=row(body,text(R.string.CommunityPendingMore),()->{visible+=20;render();});
        row(body,text(R.string.CommunityPendingClear),()->{if(valid()&&!busy)showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.CommunityPendingClear)).setMessage(text(R.string.CommunityPendingClearInfo)).setPositiveButton(text(R.string.Delete),(d,w)->{busy=true;CommunityPendingReplies.clear(currentAccount,owner,this::changed);}).setNegativeButton(text(R.string.Cancel),null).create());});
        refresh();return scroll;
    }
    private boolean valid(){return alive&&CommunityPendingReplies.active(currentAccount,owner);}
    private TextView row(LinearLayout parent,String label,Runnable action){TextView view=new TextView(parent.getContext());view.setText(label);view.setTextSize(16);view.setTextColor(Theme.getColor(action==null?Theme.key_windowBackgroundWhiteBlackText:Theme.key_windowBackgroundWhiteBlueText));view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));parent.addView(view,LayoutHelper.createLinear(-1,-2));if(action!=null){CommunitySettingsUi.button(view);view.setOnClickListener(v->{if(valid())action.run();});}return view;}
    private void refresh(){if(status==null||!valid())return;final int ticket=++generation;status.setText(text(R.string.Loading));CommunityPendingReplies.load(currentAccount,owner,list->{if(!valid()||ticket!=generation)return;if(list==null){status.setText(text(R.string.CommunityPendingFailed));return;}entries=list;status.setText("");render();});}
    private void changed(boolean ok){if(!valid())return;busy=false;if(!ok)Toast.makeText(getParentActivity(),text(R.string.CommunityPendingFailed),Toast.LENGTH_LONG).show();refresh();}
    private String label(PendingReplyData.Entry entry){
        String name=null;MessagesController controller=MessagesController.getInstance(currentAccount);
        if(entry.dialogId==owner)name=text(R.string.SavedMessages);
        else if(entry.dialogId>0){TLRPC.User user=controller.getUser(entry.dialogId);if(user!=null)name=UserObject.getUserName(user);}else{TLRPC.Chat chat=controller.getChat(-entry.dialogId);if(chat!=null)name=chat.title;}
        String reference=name==null||name.isEmpty()?LocaleController.formatString("CommunityPendingReference",R.string.CommunityPendingReference,entry.dialogId,entry.messageId):name+" · #"+entry.messageId;
        return reference+(entry.note.isEmpty()?"":"\n"+entry.note);
    }
    private void render(){if(results==null||more==null||!valid())return;results.removeAllViews();int matched=0;for(PendingReplyData.Entry entry:entries)if(entry.status==filter){if(matched++<visible)row(results,label(entry),()->actions(entry));}if(matched==0)row(results,text(R.string.CommunityPendingEmpty),null);more.setVisibility(matched>visible?View.VISIBLE:View.GONE);}
    private void actions(PendingReplyData.Entry entry){if(busy||!valid())return;showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(label(entry)).setItems(new CharSequence[]{text(R.string.CommunityPendingOpen),text(R.string.CommunityPendingStatus),text(R.string.CommunityPendingNote),text(R.string.Delete)},(dialog,which)->{
        if(!valid()||busy)return;
        if(which==0)open(entry);
        else if(which==1)showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.CommunityPendingStatus)).setItems(states(),(d,index)->{if(valid()&&!busy){busy=true;CommunityPendingReplies.update(currentAccount,owner,entry.key(),index,entry.note,this::changed);}}).create());
        else if(which==2)note(entry);
        else showDialog(new AlertDialog.Builder(getParentActivity()).setMessage(text(R.string.CommunityPendingRemoveInfo)).setPositiveButton(text(R.string.Delete),(d,w)->{if(valid()&&!busy){busy=true;CommunityPendingReplies.remove(currentAccount,owner,entry.key(),this::changed);}}).setNegativeButton(text(R.string.Cancel),null).create());
    }).create());}
    private void note(PendingReplyData.Entry entry){EditText input=new EditText(getParentActivity());input.setText(entry.note);input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));input.setHint(text(R.string.CommunityPendingNoteHint));input.setContentDescription(text(R.string.CommunityPendingNoteHint));input.setMinLines(3);input.setMaxLines(8);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PendingReplyData.MAX_NOTE)});showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.CommunityPendingNote)).setView(input).setPositiveButton(text(R.string.Save),(d,w)->{if(valid()&&!busy){busy=true;CommunityPendingReplies.update(currentAccount,owner,entry.key(),entry.status,input.getText().toString(),this::changed);}}).setNegativeButton(text(R.string.Cancel),null).create());}
    private void open(PendingReplyData.Entry entry){if(!valid())return;Bundle args=new Bundle();args.putLong(entry.dialogId>0?"user_id":"chat_id",Math.abs(entry.dialogId));args.putInt("message_id",entry.messageId);if(entry.topicId>0)args.putLong("topic_id",entry.topicId);if(getMessagesController().checkCanOpenChat(args,this)){ChatActivity chat=new ChatActivity(args);chat.setCurrentAccount(currentAccount);presentFragment(chat);}else Toast.makeText(getParentActivity(),text(R.string.CommunityPendingUnavailable),Toast.LENGTH_LONG).show();}
    @Override public void onResume(){super.onResume();if(owner!=0&&!valid()){finishFragment();return;}refresh();}
    @Override public void onFragmentDestroy(){alive=false;generation++;entries.clear();NotificationCenter.getInstance(currentAccount).removeObserver(this,NotificationCenter.appDidLogout);super.onFragmentDestroy();}
}
