package org.telegram.ui;

import android.content.Context;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.CommunitySettingsUi;
import org.telegram.ui.Components.CommunityThemePreview;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.web.CommunityThemeLoader;
import java.util.*;

/** Explicit public catalog, no background subscription and no automatic theme application. */
public class CommunityThemesActivity extends BaseFragment {
    private final String initialId;
    private LinearLayout list;
    private TextView status,more;
    private final ArrayList<CommunityThemeLoader.Item> items=new ArrayList<>();
    private String cursor="";
    private Runnable cancel;
    private int generation;
    private boolean alive=true,busy;
    public CommunityThemesActivity(){this(null);}
    public CommunityThemesActivity(String id){initialId=id;}
    @Override public android.view.View createView(Context context){
        actionBar.setTitle(LocaleController.getString(R.string.CommunityThemesTitle));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);fragmentView=scroll;
        label(body,LocaleController.getString(R.string.CommunityThemesInfo));status=label(body,"");
        button(body,LocaleController.getString(R.string.Refresh),()->load(true));
        list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);body.addView(list,LayoutHelper.createLinear(-1,-2));
        more=button(body,LocaleController.getString(R.string.CommunityThemesMore),()->load(false));more.setVisibility(android.view.View.GONE);
        if(initialId!=null)openDetail(initialId);else load(true);return scroll;
    }
    private TextView label(LinearLayout parent,String text){
        TextView view=new TextView(parent.getContext());view.setText(text);view.setTextSize(16);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));parent.addView(view,LayoutHelper.createLinear(-1,-2));return view;
    }
    private TextView button(LinearLayout parent,String text,Runnable action){TextView view=label(parent,text);CommunitySettingsUi.button(view);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));view.setOnClickListener(v->{if(!busy)action.run();});return view;}
    private int begin(){if(cancel!=null)cancel.run();busy=true;status.setText(LocaleController.getString(R.string.Loading));return ++generation;}
    private void load(boolean reset){
        if(!reset&&cursor.isEmpty())return;int request=begin();if(reset){items.clear();cursor="";list.removeAllViews();}
        cancel=CommunityThemeLoader.list(cursor,page->{if(!alive||request!=generation)return;busy=false;
            if(page==null){failed();return;}for(CommunityThemeLoader.Item item:page.items){boolean exists=false;for(CommunityThemeLoader.Item old:items)if(old.id.equals(item.id)){exists=true;break;}if(!exists&&items.size()<100)items.add(item);}
            cursor=page.nextCursor;list.removeAllViews();for(CommunityThemeLoader.Item item:items){label(list,item.title+(item.author.isEmpty()?"":" · "+item.author));button(list,LocaleController.getString(R.string.CommunityThemesPreview),()->openDetail(item.id));}
            status.setText(items.isEmpty()?LocaleController.getString(R.string.CommunityThemesEmpty):"");more.setVisibility(!cursor.isEmpty()&&items.size()<100?android.view.View.VISIBLE:android.view.View.GONE);
        });
    }
    private void openDetail(String id){int request=begin();cancel=CommunityThemeLoader.detail(id,item->{if(!alive||request!=generation)return;
        if(item==null){busy=false;failed();return;}
        cancel=CommunityThemePreview.open(this,item,ok->{if(!alive||request!=generation)return;busy=false;if(!ok)failed();else status.setText("");});
    });}
    private void failed(){status.setText(LocaleController.getString(R.string.CommunityThemesFailed));}
    @Override public void onFragmentDestroy(){alive=false;generation++;if(cancel!=null)cancel.run();super.onFragmentDestroy();}
}
