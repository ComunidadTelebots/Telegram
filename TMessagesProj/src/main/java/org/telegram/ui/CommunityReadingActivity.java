package org.telegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.CommunitySettingsUi;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.web.OfflineArticleStore;
import org.telegram.ui.web.OfflineReadingFilter;
import java.util.ArrayList;

/** Account-private library. Search/filter is in-memory; storage snapshots run off the UI thread. */
public class CommunityReadingActivity extends BaseFragment {
    private LinearLayout body, results;
    private TextView storage, resultCount;
    private boolean alive=true, opening;
    private int refreshGeneration, filter;
    private String query="";
    private ArrayList<OfflineArticleStore.Entry> entries=new ArrayList<>();

    @Override public View createView(Context context) {
        actionBar.setTitle(text(R.string.CommunityReadingLibrary));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);
        body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));fragmentView=scroll;
        label(body,text(R.string.CommunityReadingLocalOnly));
        label(body,text(R.string.CommunityReadingLimits));
        label(body,text(R.string.CommunityReadingNativeInfo));
        button(body,R.string.WebBookmarks,()->{
            if(currentAccount!=UserConfig.selectedAccount){toast(false);return;}
            presentFragment(new org.telegram.ui.web.BookmarksFragment(null,url->Browser.openUrl(getParentActivity(),url)));
        });
        button(body,R.string.SavedMessages,()->{
            ChatActivity saved=ChatActivity.of(UserConfig.getInstance(currentAccount).getClientUserId());
            saved.setCurrentAccount(currentAccount);presentFragment(saved);
        });
        storage=label(body,text(R.string.CommunityReadingCalculating));
        button(body,R.string.Refresh,this::refresh);
        button(body,R.string.CommunityReadingAdd,this::addLink);
        EditText search=new EditText(context);
        search.setTextSize(16);search.setSingleLine(true);search.setHint(text(R.string.CommunityReadingSearch));
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        search.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));
        search.setMinHeight(AndroidUtilities.dp(48));search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(256)});
        search.setText(query);body.addView(search,LayoutHelper.createLinear(-1,-2));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();renderEntries();}public void afterTextChanged(Editable e){}});
        Spinner filters=new Spinner(context);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_item,new String[]{text(R.string.CommunityReadingFilterAll),text(R.string.CommunityReadingFilterOffline),text(R.string.CommunityReadingFilterLater),text(R.string.CommunityReadingFilterPosts)});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);filters.setAdapter(adapter);filters.setSelection(filter);
        filters.setMinimumHeight(AndroidUtilities.dp(48));filters.setContentDescription(text(R.string.CommunityReadingFilter));
        body.addView(filters,LayoutHelper.createLinear(-1,-2));
        filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){filter=position;renderEntries();}public void onNothingSelected(AdapterView<?> p){}});
        resultCount=label(body,"");results=new LinearLayout(context);results.setOrientation(LinearLayout.VERTICAL);body.addView(results,LayoutHelper.createLinear(-1,-2));
        button(body,R.string.CommunityReadingClear,this::confirmClear);
        refresh();return scroll;
    }
    private String text(int id){return LocaleController.getString(id);}
    private TextView label(LinearLayout parent,String value) {
        TextView view=new TextView(parent.getContext());view.setText(value);view.setTextSize(15);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));parent.addView(view,LayoutHelper.createLinear(-1,-2));return view;
    }
    private void button(LinearLayout parent,int title,Runnable click) {
        TextView view=label(parent,text(title));CommunitySettingsUi.button(view);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));view.setOnClickListener(v->click.run());
    }
    private void addLink() {
        EditText input=new EditText(getParentActivity());input.setHint("https://");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.CommunityReadingAdd)).setView(input).setPositiveButton(text(R.string.Add),(d,w)->{
            if(!OfflineArticleStore.queue(currentAccount,input.getText().toString().trim(),null))toast(false);refresh();
        }).setNegativeButton(text(R.string.Cancel),null).create());
    }
    private void refresh() {
        if(body==null||!alive)return;
        final int generation=++refreshGeneration;
        storage.setText(text(R.string.CommunityReadingCalculating));
        OfflineArticleStore.snapshot(currentAccount,snapshot->{
            if(!alive||generation!=refreshGeneration)return;
            entries=snapshot.entries;
            storage.setText(snapshot.bytes<0?text(R.string.CommunityReadingStorageUnavailable):LocaleController.formatString("CommunityReadingStorage",R.string.CommunityReadingStorage,AndroidUtilities.formatFileSize(snapshot.bytes)));
            renderEntries();
        });
    }
    private void renderEntries() {
        if(results==null||!alive)return;
        results.removeAllViews();int shown=0;
        for(OfflineArticleStore.Entry entry:entries) {
            boolean post=OfflineArticleStore.isTelegramLink(entry.url);
            if(!OfflineReadingFilter.matches(entry.title,entry.url,entry.offline,post,filter,query))continue;
            shown++;
            label(results,entry.title+"\n"+entry.url);
            if(entry.offline) {
                label(results,LocaleController.formatString("CommunityReadingImages",R.string.CommunityReadingImages,entry.images,entry.missing));
                button(results,R.string.CommunityReadingOpenOffline,()->openOffline(entry));
                button(results,R.string.ArticleChangesTitle,()->presentFragment(new ArticleChangesActivity(currentAccount,entry.url)));
                button(results,R.string.ReadingNotesTitle,()->org.telegram.ui.Components.ReadingNotesDialog.show(getParentActivity(),currentAccount,entry.url,dialog->{if(alive)showDialog(dialog);}));
            }
            button(results,post?R.string.CommunityReadingPostOriginal:R.string.CommunityReadingOriginal,()->Browser.openUrl(getParentActivity(),entry.url));
            if(!post)button(results,R.string.CommunityReadingDownload,()->{
                Toast.makeText(getParentActivity(),text(R.string.CommunityReadingSaving),Toast.LENGTH_SHORT).show();
                OfflineArticleStore.save(currentAccount,entry.url,ok->{if(alive){toast(ok);refresh();}});
            });
            button(results,R.string.Delete,()->showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.Delete)).setMessage(entry.title).setPositiveButton(text(R.string.Delete),(d,w)->OfflineArticleStore.remove(currentAccount,entry.url,this::refresh)).setNegativeButton(text(R.string.Cancel),null).create()));
        }
        resultCount.setText(LocaleController.formatString("CommunityReadingResults",R.string.CommunityReadingResults,shown,entries.size()));
        if(shown==0)label(results,text(entries.isEmpty()?R.string.CommunityReadingEmpty:R.string.CommunityReadingNoResults));
    }
    private void openOffline(OfflineArticleStore.Entry entry) {
        if(opening)return;opening=true;
        OfflineArticleStore.open(currentAccount,entry.url,page->{
            opening=false;
            if(!alive){if(page!=null)org.telegram.ui.web.WebInstantView.recycle(page);return;}
            if(page==null){toast(false);return;}
            ArticleViewer viewer=ArticleViewer.getInstance();viewer.setParentActivity(getParentActivity(),this);
            if(!viewer.open(page,entry.url))org.telegram.ui.web.WebInstantView.recycle(page);
        });
    }
    private void confirmClear() {
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.CommunityReadingClear)).setMessage(text(R.string.CommunityReadingClearInfo)).setPositiveButton(text(R.string.Delete),(d,w)->OfflineArticleStore.clear(currentAccount,this::refresh)).setNegativeButton(text(R.string.Cancel),null).create());
    }
    private void toast(boolean ok){if(getParentActivity()!=null)Toast.makeText(getParentActivity(),text(ok?R.string.CommunityReadingSaved:R.string.CommunityReadingFailed),Toast.LENGTH_LONG).show();}
    @Override public void onResume(){super.onResume();refresh();}
    @Override public void onFragmentDestroy(){alive=false;refreshGeneration++;super.onFragmentDestroy();}
}
