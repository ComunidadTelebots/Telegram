package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.SkinSelectorCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.utils.proxy.*;

/** Settings owned by Comunidad Telebots. Official Telegram settings stay in their screens. */
public class CommunitySettingsActivity extends BaseFragment {
    public static final int HOME=0, DESIGN=1, READING=2, CONNECTIONS=3, PRIVACY=4, TOR=5, ACCESSIBILITY=6, P2P_MESH=7;
    private final int page;
    private TextCheckCell tor;
    private TextView torStatus;
    private boolean active;
    public CommunitySettingsActivity(){this(HOME);}
    public CommunitySettingsActivity(int page){this.page=page;}
    private String text(int id){return LocaleController.getString(id);}
    private final Runnable refreshTor=new Runnable(){@Override public void run(){if(!active || tor==null)return;updateTor();AndroidUtilities.runOnUIThread(this,5000);}};
    private String titleStr(){switch(page){case DESIGN:return text(R.string.TelebotsDesign);case READING:return text(R.string.TelebotsReading);case CONNECTIONS:return text(R.string.TelebotsConnections);case PRIVACY:return text(R.string.TelebotsPrivacy);case TOR:return text(R.string.TorTitle);case ACCESSIBILITY:return text(R.string.TelebotsAccessibility);case P2P_MESH:return "Malla P2P (100 Funciones)";default:return text(R.string.TelebotsSettings);}}
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(titleStr());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);fragmentView=scroll;
        switch(page){
            case HOME:
                label(body,text(R.string.TelebotsSettingsInfo));
                entry(body,R.string.TelebotsDesign,()->open(DESIGN));
                entry(body,R.string.TelebotsReading,()->open(READING));
                entry(body,R.string.TelebotsConnections,()->open(CONNECTIONS));
                entry(body,R.string.TelebotsPhrases,()->presentFragment(new CommunityQuickPhrasesActivity()));
                entry(body,R.string.TelebotsDownloads,()->presentFragment(new CommunityDownloadsActivity()));
                entry(body,"Malla P2P (100 Funciones)",()->open(P2P_MESH));
                entry(body,R.string.TelebotsPerformance,()->presentFragment(new CommunityPerformanceActivity()));
                entry(body,R.string.TelebotsAccessibility,()->open(ACCESSIBILITY));
                entry(body,R.string.TelebotsUpdates,()->presentFragment(new AppUpdatesActivity()));
                entry(body,R.string.TelebotsPrivacy,()->open(PRIVACY));
                entry(body,R.string.TelebotsBackupTitle,()->presentFragment(new CommunitySettingsBackupActivity()));
                entry(body,R.string.TelebotsOfficialChannel,()->getMessagesController().openByUserName("comunidadtelebots",this,0));
                break;
            case DESIGN:
                label(body, "Diseños Históricos y Perfiles Avanzados");
                entry(body, "Cambiar Año Histórico (Actual: " + SharedConfig.uiVersionYear + ")", () -> {
                    SharedConfig.uiVersionYear = SharedConfig.uiVersionYear >= 2026 ? 2013 : SharedConfig.uiVersionYear + 1;
                    SharedConfig.saveConfig();
                    if(parentLayout!=null)parentLayout.rebuildAllFragmentViews(true,true);
                });
                entry(body, "Cambiar Perfil de Interfaz (Actual: " + SharedConfig.uiThemeProfile + ")", () -> {
                    String[] profiles = {"telegram_modern", "ios", "telegram_x", "unigram", "macos", "cyberpunk", "oled_black", "holo_2013", "metro_fluent", "material_you"};
                    int idx = 0;
                    for (int i=0; i<profiles.length; i++) if (profiles[i].equals(SharedConfig.uiThemeProfile)) idx = i;
                    SharedConfig.uiThemeProfile = profiles[(idx + 1) % profiles.length];
                    SharedConfig.saveConfig();
                    org.telegram.ui.Components.UIProfileManager.applyActiveProfileTheme();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, Theme.getActiveTheme(), false, null, -1, null);
                    if(parentLayout!=null)parentLayout.rebuildAllFragmentViews(true,true);
                });
                entry(body, "Permitir Funciones Futuras: " + (SharedConfig.allowFutureFeatures ? "SÍ" : "NO"), () -> {
                    SharedConfig.allowFutureFeatures = !SharedConfig.allowFutureFeatures;
                    SharedConfig.saveConfig();
                    if(parentLayout!=null)parentLayout.rebuildAllFragmentViews(true,true);
                });
                body.addView(new SkinSelectorCell(context,skin->{if(parentLayout!=null)parentLayout.rebuildAllFragmentViews(true,true);}),LayoutHelper.createLinear(-1,-2));
                break;
            case P2P_MESH:
                label(body, "Estado del Nodo Local (Global)");
                TelebotsP2pHubController p2p = TelebotsP2pHubController.getInstance(currentAccount);
                entry(body, "Puntos de Karma: " + p2p.getKarmaPoints(), () -> {
                    android.widget.Toast.makeText(context, "El Karma se gana compartiendo caché de páginas AMP/InstantView.", android.widget.Toast.LENGTH_LONG).show();
                });
                entry(body, "Ancho de banda cedido: " + (p2p.getBandwidthServedBytes() / 1024 / 1024) + " MB", () -> {});
                entry(body, "Nodo P2P Principal: " + (p2p.isP2pEnabled() ? "ACTIVO" : "APAGADO"), () -> {
                    p2p.setP2pEnabled(!p2p.isP2pEnabled());
                    open(P2P_MESH);
                });

                label(body, "100 Funciones P2P, ZeroNet, Anti-Censura y Storage.");
                entry(body, "Abrir Panel de Control Avanzado (Dashboard)", () -> {
                    presentFragment(new TelebotsMeshDashboardActivity());
                });
                break;
            case READING:
                entry(body,R.string.TelebotsReadingLibrary,()->presentFragment(new CommunityReadingActivity()));
                label(body,text(R.string.TelebotsReadingInfo));
                android.widget.CheckBox saving=new android.widget.CheckBox(context);
                saving.setText(text(R.string.AmpUnmeteredOnly));saving.setSingleLine(false);
                saving.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                saving.setMinHeight(AndroidUtilities.dp(52));saving.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(12),AndroidUtilities.dp(16),AndroidUtilities.dp(12));
                saving.setChecked(PreviewNetworkPolicy.wifiOnly());
                saving.setOnCheckedChangeListener((button,enabled)->MessagesController.getGlobalMainSettings().edit().putBoolean("amp_unmetered_only",enabled).apply());
                body.addView(saving);label(body,text(R.string.AmpUnmeteredInfo));
                break;
            case CONNECTIONS:
                entry(body,R.string.DnsCatalogTitle,()->presentFragment(new DnsSettingsActivity()));
                entry(body,R.string.TelebotsDomainTools,()->presentFragment(new NetworkDiagnosticsActivity(false)));
                entry(body,R.string.TelebotsProxyTools,()->presentFragment(new NetworkDiagnosticsActivity(true)));
                entry(body,R.string.TorTitle,()->open(TOR));
                break;
            case PRIVACY:
                label(body,text(R.string.TelebotsAccessInfo)+"\n"+LocaleController.formatString("TelebotsUpdateChannel",R.string.TelebotsUpdateChannel,ReleaseAccess.channel()));
                entry(body,R.string.TelebotsAccessPanel,()->Browser.openUrl(getParentActivity(),ReleaseAccess.ORIGIN+"/access/"));
                entry(body,R.string.PrivacyPolicy,()->Browser.openUrl(getParentActivity(),ReleaseAccess.ORIGIN+"/access/privacy"));
                entry(body,R.string.TermsOfService,()->Browser.openUrl(getParentActivity(),ReleaseAccess.ORIGIN+"/access/terms"));
                break;
            case ACCESSIBILITY:
                label(body,text(R.string.TelebotsAccessibilityInfo));
                entry(body,R.string.TelebotsSystemAccessibility,()->{
                    try { getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                    catch(Exception ignored) { }
                });
                entry(body,R.string.TelebotsTextAppearance,()->presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)));
                break;
            case TOR:
                tor=new TextCheckCell(context);tor.setTextAndCheck(text(R.string.TorTitle),TorProxyController.isEnabled(),false);tor.setEnabled(TorProxyController.isSupported());
                tor.setOnClickListener(v->{
                    if(!TorProxyController.isEnabled() && org.telegram.messenger.voip.VoIPService.getSharedInstance()!=null){android.widget.Toast.makeText(context,text(R.string.TorEndCallFirst),android.widget.Toast.LENGTH_LONG).show();return;}
                    if(!TorProxyController.isEnabled()){Intent consent=android.net.VpnService.prepare(context);if(consent!=null){startActivityForResult(consent,8392);return;}}
                    TorProxyController.setEnabled(!TorProxyController.isEnabled());updateTor();
                });
                body.addView(tor);torStatus=label(body,"");label(body,text(R.string.TorScope));updateTor();break;
        }
        return scroll;
    }
    private void open(int value){presentFragment(new CommunitySettingsActivity(value));}
    private TextView label(LinearLayout body,String value){TextView v=new TextView(body.getContext());v.setText(value);v.setTextSize(15);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));v.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(14),AndroidUtilities.dp(20),AndroidUtilities.dp(14));body.addView(v,LayoutHelper.createLinear(-1,-2));return v;}
    private void entry(LinearLayout body,int title,Runnable click){entry(body, text(title), click);}
    private void entry(LinearLayout body,String title,Runnable click){TextView v=label(body,title);v.setMinHeight(AndroidUtilities.dp(52));v.setGravity(Gravity.CENTER_VERTICAL);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));v.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector),2));v.setOnClickListener(w->click.run());org.telegram.ui.Components.CommunitySettingsUi.button(v);}
    private void updateTor(){if(tor==null || torStatus==null)return;tor.setChecked(TorProxyController.isEnabled());String state=text(TorProxyController.isSupported()?TorProxyController.getStatus():R.string.TorUnsupported);if(TorProxyController.isEnabled() && TorProxyController.getStatus()==R.string.TorStarting)state+=" "+TorProxyController.getProgress()+"%";torStatus.setText(android.text.TextUtils.concat(state,"\n",text(R.string.TorServerLatency)," ",org.telegram.ui.Components.ConnectionLatency.badge(currentAccount)));}
    @Override public void onActivityResultFragment(int code,int result,Intent data){super.onActivityResultFragment(code,result,data);if(page==TOR && code==8392 && result==android.app.Activity.RESULT_OK && org.telegram.messenger.voip.VoIPService.getSharedInstance()==null){TorProxyController.setEnabled(true);updateTor();}}
    @Override public void onResume(){super.onResume();active=true;if(page==TOR){TorProxyController.ensureRunning();AndroidUtilities.cancelRunOnUIThread(refreshTor);refreshTor.run();}}
    @Override public void onPause(){active=false;AndroidUtilities.cancelRunOnUIThread(refreshTor);super.onPause();}
    @Override public void onFragmentDestroy(){active=false;AndroidUtilities.cancelRunOnUIThread(refreshTor);super.onFragmentDestroy();}
}
