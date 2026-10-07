package org.telegram.ui;

import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.LayoutHelper;
import java.util.ArrayList;

public class CommunityQuickPhrasesActivity extends BaseFragment {
    private LinearLayout body, results;
    private EditText search;
    private String text(int id){return LocaleController.getString(id);}
    @Override public View createView(Context context){
        actionBar.setTitle(text(R.string.TelebotsPhrases));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);fragmentView=scroll;
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row(text(R.string.TelebotsPhrasesInfo),null);row(text(R.string.TelebotsPhraseAdd),()->edit(null));
        row(text(R.string.TelebotsPhraseExamples),()->{
            int added=CommunityQuickPhrases.addExamples(currentAccount,text(R.string.TelebotsPhraseGeneral),new String[]{
                    text(R.string.TelebotsPhraseReplySoon),text(R.string.TelebotsPhraseDetails),text(R.string.TelebotsPhraseThanks)});
            Toast.makeText(body.getContext(),LocaleController.formatString("TelebotsPhraseExamplesAdded",R.string.TelebotsPhraseExamplesAdded,added),Toast.LENGTH_SHORT).show();refresh();
        });
        search=searchField(body,()->refresh());
        button(body,text(R.string.TelebotsPhraseClearSearch),()->search.setText(""));
        results=new LinearLayout(context);results.setOrientation(LinearLayout.VERTICAL);body.addView(results,LayoutHelper.createLinear(-1,-2));
        refresh();return scroll;
    }
    private TextView row(String value,Runnable click){return button(body,value,click);}
    private static TextView button(LinearLayout parent,String value,Runnable click){
        TextView view=new TextView(parent.getContext());view.setText(value);view.setTextSize(16);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(14),AndroidUtilities.dp(20),AndroidUtilities.dp(14));view.setMinHeight(AndroidUtilities.dp(52));
        if(click!=null){view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector),2));view.setOnClickListener(v->click.run());org.telegram.ui.Components.CommunitySettingsUi.button(view);}
        parent.addView(view,LayoutHelper.createLinear(-1,-2));return view;
    }
    private static EditText searchField(LinearLayout parent,Runnable update){
        EditText field=new EditText(parent.getContext());field.setSingleLine(true);field.setTextSize(16);
        field.setHint(LocaleController.getString(R.string.TelebotsPhraseSearch));field.setContentDescription(LocaleController.getString(R.string.TelebotsPhraseSearch));
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        field.setMinHeight(AndroidUtilities.dp(52));field.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        parent.addView(field,LayoutHelper.createLinear(-1,-2));
        field.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){}
            @Override public void afterTextChanged(android.text.Editable s){update.run();}
        });return field;
    }
    private static String phraseLabel(CommunityQuickPhrases.Phrase phrase){
        return (phrase.favorite?LocaleController.getString(R.string.TelebotsPhraseFavorite)+" · ":"")+phrase.category+"\n"+phrase.text;
    }
    private void refresh(){
        if(results==null)return;results.removeAllViews();
        ArrayList<CommunityQuickPhrases.Phrase> all=CommunityQuickPhrases.list(currentAccount);
        ArrayList<CommunityQuickPhrases.Phrase> filtered=CommunityQuickPhrases.search(all,search.getText().toString());
        if(filtered.isEmpty())button(results,text(all.isEmpty()?R.string.TelebotsPhraseEmpty:R.string.TelebotsPhraseNoResults),null);
        for(CommunityQuickPhrases.Phrase phrase:filtered)button(results,phraseLabel(phrase),()->showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TelebotsPhraseActions))
                .setItems(new CharSequence[]{text(R.string.Edit),text(phrase.favorite?R.string.TelebotsPhraseRemoveFavorite:R.string.TelebotsPhraseAddFavorite),text(R.string.Delete)},(dialog,which)->{
                    if(which==0)edit(phrase);
                    else if(which==1){CommunityQuickPhrases.setFavorite(currentAccount,phrase.id,!phrase.favorite);refresh();}
                    else showDialog(new AlertDialog.Builder(getParentActivity()).setMessage(text(R.string.TelebotsPhraseDelete))
                            .setPositiveButton(text(R.string.Delete),(d,w)->{CommunityQuickPhrases.remove(currentAccount,phrase.id);refresh();}).setNegativeButton(text(R.string.Cancel),null).create());
                }).create()));
    }
    private void edit(CommunityQuickPhrases.Phrase phrase){
        LinearLayout form=new LinearLayout(getParentActivity());form.setOrientation(LinearLayout.VERTICAL);form.setPadding(AndroidUtilities.dp(20),0,AndroidUtilities.dp(20),0);
        EditText category=new EditText(getParentActivity()),content=new EditText(getParentActivity());
        category.setHint(text(R.string.TelebotsPhraseCategory));category.setContentDescription(text(R.string.TelebotsPhraseCategory));category.setSingleLine(true);category.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        content.setHint(text(R.string.TelebotsPhraseText));content.setContentDescription(text(R.string.TelebotsPhraseText));content.setMinLines(3);content.setMaxLines(8);content.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        category.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));content.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if(phrase!=null){category.setText(phrase.category);content.setText(phrase.text);}
        form.addView(category,LayoutHelper.createLinear(-1,-2));form.addView(content,LayoutHelper.createLinear(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TelebotsPhraseAdd)).setView(form)
                .setPositiveButton(text(R.string.Save),(d,which)->{
                    if(CommunityQuickPhrases.save(currentAccount,phrase==null?null:phrase.id,category.getText().toString(),content.getText().toString())){d.dismiss();refresh();}
                    else content.setError(text(R.string.TelebotsPhraseLimit));
                }).setNegativeButton(text(R.string.Cancel),(d,which)->d.dismiss()).create();
        dialog.setDismissDialogByButtons(false);showDialog(dialog);
    }
    public static void pick(BaseFragment fragment,int account,Utilities.Callback<String> insert){
        if(fragment.getParentActivity()==null)return;
        ArrayList<CommunityQuickPhrases.Phrase> phrases=CommunityQuickPhrases.list(account);
        LinearLayout content=new LinearLayout(fragment.getParentActivity());content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout list=new LinearLayout(fragment.getParentActivity());list.setOrientation(LinearLayout.VERTICAL);
        EditText[] query=new EditText[1];AlertDialog[] dialog=new AlertDialog[1];
        Runnable update=()->{
            list.removeAllViews();
            ArrayList<CommunityQuickPhrases.Phrase> filtered=CommunityQuickPhrases.search(phrases,query[0]==null?"":query[0].getText().toString());
            if(filtered.isEmpty())button(list,LocaleController.getString(phrases.isEmpty()?R.string.TelebotsPhraseEmpty:R.string.TelebotsPhraseNoResults),null);
            for(CommunityQuickPhrases.Phrase phrase:filtered)button(list,phraseLabel(phrase),()->{dialog[0].dismiss();insert.run(phrase.text);});
        };
        query[0]=searchField(content,update);
        button(content,LocaleController.getString(R.string.TelebotsPhraseClearSearch),()->query[0].setText(""));
        ScrollView scroll=new ScrollView(fragment.getParentActivity());scroll.addView(list);content.addView(scroll,LayoutHelper.createLinear(-1,280));
        dialog[0]=new AlertDialog.Builder(fragment.getParentActivity()).setTitle(LocaleController.getString(R.string.TelebotsPhrases)).setView(content)
                .setNegativeButton(LocaleController.getString(R.string.Cancel),null).create();
        update.run();fragment.showDialog(dialog[0]);
    }
}
