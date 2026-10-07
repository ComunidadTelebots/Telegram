package org.telegram.ui.Components;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ThemePreviewActivity;
import org.telegram.ui.web.CommunityThemeLoader;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reuses Telegram's temporary theme preview and its explicit Apply/Cancel controls. */
public final class CommunityThemePreview {
    private static boolean preparing;
    private static Theme.ThemeInfo active;
    private CommunityThemePreview() { }
    public static Runnable open(BaseFragment owner,CommunityThemeLoader.Item item,Utilities.Callback<Boolean> done){
        AtomicBoolean cancelled=new AtomicBoolean();
        if(owner==null||owner.isFinished||!owner.isLastFragment()||preparing||active!=null){done.run(false);return ()->{};}
        for(BaseFragment fragment:owner.getParentLayout().getFragmentStack())if(fragment instanceof ThemePreviewActivity){done.run(false);return ()->{};}
        if(item.telegramUrl!=null){Browser.openUrl(owner.getParentActivity(),item.telegramUrl);done.run(true);return ()->{};}
        preparing=true;
        Utilities.globalQueue.postRunnable(()->{
            File file=null;
            try {
                file=File.createTempFile("community_theme_",".attheme",ApplicationLoader.applicationContext.getCacheDir());
                try(FileOutputStream stream=new FileOutputStream(file)){stream.write(item.document);}
            }catch(Exception ignored){if(file!=null)file.delete();file=null;}
            final File ready=file;
            AndroidUtilities.runOnUIThread(()->{
                preparing=false;
                if(cancelled.get()||owner.isFinished||!owner.isLastFragment()||ready==null){if(ready!=null)ready.delete();if(!cancelled.get())done.run(false);return;}
                for(BaseFragment fragment:owner.getParentLayout().getFragmentStack())if(fragment instanceof ThemePreviewActivity){ready.delete();done.run(false);return;}
                Theme.ThemeInfo info=null;
                try {
                    info=Theme.applyThemeFile(ready,"community_"+item.id+"_"+item.revision,null,true);
                    if(info==null)throw new IllegalStateException("Theme preview");active=info;final Theme.ThemeInfo previewInfo=info;
                    ThemePreviewActivity preview=new ThemePreviewActivity(info,true,ThemePreviewActivity.SCREEN_TYPE_PREVIEW,false,false){
                        @Override public void onFragmentDestroy(){
                            if(Theme.getCurrentTheme()==previewInfo)Theme.applyPreviousTheme();
                            if(active==previewInfo)active=null;
                            super.onFragmentDestroy();ready.delete();
                        }
                    };
                    if(!owner.presentFragment(preview))throw new IllegalStateException("Theme navigation");done.run(true);
                }catch(Exception ignored){if(info!=null&&Theme.getCurrentTheme()==info)Theme.applyPreviousTheme();active=null;ready.delete();done.run(false);}
            });
        });
        return ()->cancelled.set(true);
    }
}
