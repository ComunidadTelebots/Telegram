package org.telegram.ui.web;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.telegram.ui.ArticleViewer;
import org.telegram.tgnet.TLRPC;

/** Opt-in test of an actual public news article through the production parser and viewer. */
public class AmpReaderDeviceTest extends Instrumentation {
    private String article,chart;
    private boolean backendAccountsOnly;
    private boolean photoAmpOnly;
    private String photoAmpStage="input";
    private int hold;
    private boolean languageOnly, performanceOnly, communityToolsOnly, communityUndoDiagnoseOnly, ampDiscoveryOnly, ampTlsProbeOnly, diagnosticArticleProvided;
    private void report(String message) { Bundle b = new Bundle(); b.putString("stream", message + "\n"); sendStatus(0, b); }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);backendAccountsOnly="true".equals(arguments.getString("backendAccountsOnly"));photoAmpOnly="true".equals(arguments.getString("photoAmpOnly"));ampTlsProbeOnly="true".equals(arguments.getString("ampTlsProbeOnly"));ampDiscoveryOnly="true".equals(arguments.getString("ampDiscoveryOnly"));diagnosticArticleProvided=arguments.containsKey("article");communityUndoDiagnoseOnly="true".equals(arguments.getString("communityUndoDiagnoseOnly"));communityToolsOnly="true".equals(arguments.getString("communityToolsOnly"));performanceOnly="true".equals(arguments.getString("performanceOnly"));languageOnly=arguments.getBoolean("languageOnly",false)||"true".equals(arguments.getString("languageOnly"));hold=Math.max(0,Math.min(180,Integer.parseInt(arguments.getString("hold","20")))); chart=arguments.getString("chart","28335615"); article=arguments.getString("article", "https://amp.rtve.es/noticias/20260420/compra-vivienda-espana-pocas-casas-precios-altos/17012158.shtml"); start(); }
    private org.telegram.ui.ActionBar.BaseFragment lastFragment(){
        try{return (org.telegram.ui.ActionBar.BaseFragment)Class.forName("org.telegram.ui.LaunchActivity").getMethod("getLastFragment").invoke(null);}catch(Exception e){throw new RuntimeException(e);}
    }
    private Object diagnosticField(Object object,String name)throws Exception {
        if(object==null)return null;
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private Object diagnosticCall(Object object,String name)throws Exception {
        if(object==null)return null;
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{java.lang.reflect.Method method=type.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
    /** Local-only fixture: never inserted in storage, drafts or SendMessagesHelper. */
    private void photoAmpFixture(Activity activity) throws Exception {
        if(!diagnosticArticleProvided||!AmpUrls.isEligible(article))throw new AssertionError("Explicit public article required");
        final android.widget.FrameLayout[] overlay=new android.widget.FrameLayout[1];
        final org.telegram.ui.Cells.ChatMessageCell[] cell=new org.telegram.ui.Cells.ChatMessageCell[1];
        final ArticleViewer viewer=ArticleViewer.getInstance();
        final Runnable[] cancel=new Runnable[1];
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean tapped=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            photoAmpStage="setup";
            runOnMainSync(()->{try{
                if(viewer.isVisible())throw new AssertionError("Close existing reader before fixture");
                int account=org.telegram.messenger.UserConfig.selectedAccount;
                long owner=org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
                if(owner==0)throw new AssertionError("Signed in account required");
                TLRPC.TL_message message=new TLRPC.TL_message();
                message.id=-1900000001;message.dialog_id=owner;message.date=(int)(System.currentTimeMillis()/1000);message.flags=257;
                message.from_id=new TLRPC.TL_peerUser();message.from_id.user_id=owner;
                message.peer_id=new TLRPC.TL_peerUser();message.peer_id.user_id=owner;
                message.message=article;
                TLRPC.TL_messageEntityUrl entity=new TLRPC.TL_messageEntityUrl();entity.offset=0;entity.length=article.length();message.entities.add(entity);
                message.media=new TLRPC.TL_messageMediaPhoto();message.media.flags|=3;
                message.media.photo=new TLRPC.TL_photo();message.media.photo.file_reference=new byte[0];
                TLRPC.TL_photoSize size=new TLRPC.TL_photoSize();size.w=360;size.h=140;size.type="s";size.location=new TLRPC.TL_fileLocationUnavailable();message.media.photo.sizes.add(size);
                org.telegram.messenger.MessageObject object=new org.telegram.messenger.MessageObject(account,message,true,false);object.useCustomPhoto=true;
                overlay[0]=new android.widget.FrameLayout(activity);overlay[0].setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));
                cell[0]=new org.telegram.ui.Cells.ChatMessageCell(activity,account);
                cell[0].setDelegate(new org.telegram.ui.Cells.ChatMessageCell.ChatMessageCellDelegate(){
                    @Override public boolean canPerformActions(){return true;}
                    @Override public void didPressAmpButton(org.telegram.ui.Cells.ChatMessageCell source,String url){
                        MessageLinkPreview.Result preview=MessageLinkPreview.peek(source.getReaderPreviewMessageObject());
                        if(preview==null||preview.amp==null||!android.text.TextUtils.equals(preview.url,url))return;
                        tapped.set(true);viewer.setParentActivity(activity,null);cancel[0]=viewer.openAmpUrl(url);
                    }
                });
                cell[0].setMessageObject(object,null,false,false,true);
                overlay[0].addView(cell[0],new android.widget.FrameLayout.LayoutParams(-1,-2));
                ((ViewGroup)activity.findViewById(android.R.id.content)).addView(overlay[0],new ViewGroup.LayoutParams(-1,-1));
            }catch(Throwable t){failure.set(t);}});
            if(failure.get()!=null)throw new AssertionError("Fixture setup failed",failure.get());
            photoAmpStage="discovery";
            boolean ready=false;
            for(int attempt=0;attempt<60&&!ready;attempt++){
                Thread.sleep(1000);final boolean[] state={false};
                runOnMainSync(()->{try{
                    cell[0].updateReaderPreviewVisibility(true);
                    android.graphics.RectF bounds=(android.graphics.RectF)diagnosticField(cell[0],"ampButtonRect");
                    state[0]=Boolean.TRUE.equals(diagnosticCall(cell[0],"hasReaderAmp"))&&Boolean.TRUE.equals(diagnosticCall(cell[0],"hasPhotoReader"))&&((Integer)diagnosticField(cell[0],"photoReaderHeight"))>0&&!bounds.isEmpty();
                }catch(Throwable t){failure.set(t);}});
                if(failure.get()!=null)throw new AssertionError("Fixture inspection failed",failure.get());
                ready=state[0];
            }
            report("PHOTO_AMP_CARD_READY="+ready);if(!ready)throw new AssertionError("Photo reader unavailable");
            photoAmpStage="reattach";
            // Reattachment must restart visibility/discovery without stale callbacks or loss of card.
            runOnMainSync(()->{overlay[0].removeView(cell[0]);overlay[0].addView(cell[0],new android.widget.FrameLayout.LayoutParams(-1,-2));});
            Thread.sleep(1500);
            runOnMainSync(()->{try{
                cell[0].updateReaderPreviewVisibility(true);
                android.graphics.RectF bounds=(android.graphics.RectF)diagnosticField(cell[0],"ampButtonRect");
                boolean valid=!bounds.isEmpty()&&bounds.left>=0&&bounds.top>=0&&bounds.right<=cell[0].getWidth()&&bounds.bottom<=cell[0].getHeight();
                report("PHOTO_AMP_REATTACH_BOUNDS_VALID="+valid);if(!valid)throw new AssertionError("Invalid AMP hit bounds");
            }catch(Throwable t){failure.set(t);}});
            if(failure.get()!=null)throw new AssertionError("Reattachment failed",failure.get());
            report("PHOTO_AMP_CAPTURE_READY=true");Thread.sleep(Math.min(hold,30)*1000L);
            photoAmpStage="touch";
            runOnMainSync(()->{try{
                android.graphics.RectF bounds=(android.graphics.RectF)diagnosticField(cell[0],"ampButtonRect");
                long now=android.os.SystemClock.uptimeMillis();
                android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
                android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+30,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
                try{cell[0].onTouchEvent(down);cell[0].onTouchEvent(up);}finally{down.recycle();up.recycle();}
            }catch(Throwable t){failure.set(t);}});
            if(failure.get()!=null||!tapped.get())throw new AssertionError("AMP touch did not dispatch");
            report("PHOTO_AMP_TOUCH_DISPATCHED=true");photoAmpStage="native_reader";
            final boolean[] content={false};
            for(int attempt=0;attempt<45&&!content[0];attempt++){
                Thread.sleep(1000);
                runOnMainSync(()->{
                    if(!viewer.isVisible())return;
                    for(Object page:viewer.pagesStack)if(page instanceof TLRPC.WebPage){
                        TLRPC.WebPage web=(TLRPC.WebPage)page;
                        if(web.cached_page!=null)for(Object block:web.cached_page.blocks)if(block instanceof org.telegram.tgnet.tl.TL_iv.pageBlockParagraph)content[0]=true;
                    }
                });
            }
            report("PHOTO_AMP_NATIVE_PARAGRAPH_PRESENT="+content[0]);
            if(!content[0])throw new AssertionError("Native article not opened");
            photoAmpStage="complete";
            Thread.sleep(Math.min(hold,30)*1000L);
        } finally {
            runOnMainSync(()->{if(cancel[0]!=null)cancel[0].run();if(tapped.get()&&viewer.isVisible())viewer.close(false,true);if(overlay[0]!=null&&overlay[0].getParent() instanceof ViewGroup)((ViewGroup)overlay[0].getParent()).removeView(overlay[0]);});
        }
    }
    private void tlsProbeMatrix() throws Exception {
        final String normalized=AmpUrls.normalize(article);
        if(!diagnosticArticleProvided||!AmpUrls.isEligible(normalized)){report("TLS_PROBE_INPUT_VALID=false");return;}
        if(org.telegram.utils.proxy.TorProxyController.isEnabled()||org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled",false)){
            report("TLS_PROBE_DIRECT_ROUTE_ALLOWED=false");return;
        }
        final String route=org.telegram.utils.proxy.DnsConfig.policy();
        final java.net.URI uri=java.net.URI.create(normalized);final String host=uri.getHost();
        final long deadline=android.os.SystemClock.elapsedRealtime()+55000;
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AMP-TLS-test");t.setDaemon(true);return t;});
        final AtomicReference<java.net.Socket> active=new AtomicReference<>();
        try {
            java.util.concurrent.Future<java.net.InetAddress[]> dns=worker.submit(()->org.telegram.utils.proxy.DnsConfig.selected().isEmpty()?java.net.InetAddress.getAllByName(host):org.telegram.utils.proxy.DnsConfig.resolve(host));
            java.net.InetAddress[] addresses;
            try{addresses=dns.get(8,TimeUnit.SECONDS);}catch(Exception e){dns.cancel(true);throw e;}
            if(addresses.length==0)throw new java.net.UnknownHostException();
            for(java.net.InetAddress address:addresses)if(!AmpUrls.isPublicAddress(address))throw new java.io.IOException("Non-public DNS");
            final java.net.InetAddress pinned=addresses[0];
            String[][] probes={{"close","default"},{"keepalive","default"}};
            for(String[] probe:probes){
                if(!route.equals(org.telegram.utils.proxy.DnsConfig.policy())){report("TLS_PROBE_ROUTE_UNCHANGED=false");break;}
                long remaining=deadline-android.os.SystemClock.elapsedRealtime();if(remaining<=0)break;
                final String mode=probe[0],suite=probe[1];
                java.util.concurrent.Future<String> task=worker.submit(()->{
                    Class<?> conscrypt=Class.forName("org.conscrypt.Conscrypt");
                    java.security.Provider provider=(java.security.Provider)conscrypt.getMethod("newProvider").invoke(null);
                    javax.net.ssl.SSLContext context=javax.net.ssl.SSLContext.getInstance("TLS",provider);
                    context.init(null, android.os.Build.VERSION.SDK_INT < 24 ? LegacyPublicTlsTrust.create(getTargetContext()) : null, null);
                    javax.net.ssl.SSLSocketFactory factory=context.getSocketFactory();
                    java.net.Socket plain=new java.net.Socket();active.set(plain);
                    try {
                        if(!route.equals(org.telegram.utils.proxy.DnsConfig.policy()))throw new java.io.IOException("Route changed");
                        plain.connect(new java.net.InetSocketAddress(pinned,443),5000);plain.setSoTimeout(5000);
                        try(javax.net.ssl.SSLSocket tls=(javax.net.ssl.SSLSocket)factory.createSocket(plain,host,443,true)){
                            active.set(tls);tls.setSoTimeout(5000);tls.setEnabledProtocols(new String[]{"TLSv1.2"});
                            if(!"default".equals(suite)){
                                java.util.ArrayList<String> selected=new java.util.ArrayList<>();
                                for(String cipher:tls.getSupportedCipherSuites())if(cipher.startsWith("TLS_ECDHE_")&&("aes128gcm".equals(suite)?cipher.contains("_AES_128_GCM_"):cipher.contains("_CHACHA20_POLY1305_")))selected.add(cipher);
                                if(selected.isEmpty())return "supported=false";
                                tls.setEnabledCipherSuites(selected.toArray(new String[0]));
                            }
                            tls.startHandshake();
                            if(!javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(host,tls.getSession()))throw new javax.net.ssl.SSLHandshakeException("Hostname certificate verification failed");
                            report("TLS_NEGOTIATED mode="+mode+" suites="+suite+" protocol="+tls.getSession().getProtocol()+" cipher="+tls.getSession().getCipherSuite()+" socket="+tls.getClass().getSimpleName()+" hostname_verified=true");
                            if(!route.equals(org.telegram.utils.proxy.DnsConfig.policy()))throw new java.io.IOException("Route changed");
                            String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
                            String request="GET "+path+" HTTP/1.1\r\nHost: "+host+"\r\nAccept: text/html\r\nAccept-Encoding: identity\r\nConnection: "+("keepalive".equals(mode)?"keep-alive":"close")+"\r\nUser-Agent: ComunidadTelebots-TLS-Diagnostic\r\n\r\n";
                            tls.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));tls.getOutputStream().flush();
                            java.io.BufferedInputStream input=new java.io.BufferedInputStream(tls.getInputStream());
                            String status=AmpResolver.line(input);
                            java.lang.reflect.Method parseHeaders=AmpResolver.class.getDeclaredMethod("headers",java.io.InputStream.class);parseHeaders.setAccessible(true);
                            @SuppressWarnings("unchecked") java.util.Map<String,String> headers=(java.util.Map<String,String>)parseHeaders.invoke(null,input);
                            boolean framed=headers.containsKey("content-length")||"chunked".equalsIgnoreCase(headers.get("transfer-encoding"));
                            report("TLS_HTTP mode="+mode+" framed="+framed+" content_length_present="+headers.containsKey("content-length")+" chunked="+"chunked".equalsIgnoreCase(headers.get("transfer-encoding")));
                            if("keepalive".equals(mode)&&!framed)throw new java.io.IOException("Unframed keepalive response");
                            byte[] body=AmpResolver.body(input,headers);
                            return "supported=true handshake=true hostname_verified=true protocol="+tls.getSession().getProtocol()+" cipher="+tls.getSession().getCipherSuite()+" socket="+tls.getClass().getSimpleName()+" http_200="+(status.startsWith("HTTP/1.1 200")||status.startsWith("HTTP/1.0 200"))+" body_complete=true body_present="+(body.length>0);

                        }
                    } finally {try{plain.close();}catch(Exception ignored){}active.set(null);}
                });
                try {report("TLS_PROBE mode="+mode+" suites="+suite+" "+task.get(Math.min(8000,remaining),TimeUnit.MILLISECONDS));}
                catch(Exception failure){
                    java.net.Socket socket=active.get();if(socket!=null)try{socket.close();}catch(Exception ignored){}task.cancel(true);
                    report("TLS_PROBE mode="+mode+" suites="+suite+" success=false");reportAmpTransportFailure(failure);
                }
            }
        } finally {java.net.Socket socket=active.get();if(socket!=null)try{socket.close();}catch(Exception ignored){}worker.shutdownNow();}
    }
    private void reportAmpTransportFailure(Throwable failure){
        java.util.HashSet<Throwable> seen=new java.util.HashSet<>();
        for(Throwable cause=failure;cause!=null&&seen.add(cause)&&seen.size()<=5;cause=cause.getCause()){
            String message=cause.getMessage()==null?"":cause.getMessage();
            String lower=message.toLowerCase(java.util.Locale.ROOT);String reason="unclassified";
            for(String token:new String[]{"handshake_failure","protocol_version","certificate","trust anchor","subject alternative","bad_record_mac","bad_decrypt","wrong_version","unsupported_protocol","connection reset","timed out","timeout","closed","eof"}){
                if(lower.contains(token)){reason=token.replace(' ','_');break;}
            }
            // Only OpenSSL diagnostic tokens are copied; never include request URLs or arbitrary text.
            java.util.regex.Matcher code=java.util.regex.Pattern.compile("(?i)error:([0-9a-f]{8})").matcher(message);
            java.util.regex.Matcher detail=java.util.regex.Pattern.compile("OPENSSL_internal:([A-Z0-9_]{1,96})").matcher(message);
            report("AMP_ORIGIN_CAUSE type="+cause.getClass().getSimpleName()+" reason="+reason+" ssl_code="+(code.find()?code.group(1):"none")+" ssl_detail="+(detail.find()?detail.group(1):"none"));
        }
    }
    private void diagnosticBoolean(String name,boolean value){report("UNDO_GUARD "+name+"="+value);}
    /** Read-only composer inspection. No draft text, IDs, preferences or send methods are logged/called. */
    private boolean diagnoseCommunityUndo(boolean reportNow)throws Exception {
        org.telegram.ui.ActionBar.BaseFragment fragment=lastFragment();
        boolean chat=fragment instanceof org.telegram.ui.ChatActivity;
        if(!chat){if(reportNow)diagnosticBoolean("chat_visible",false);return false;}
        Object editorView=diagnosticField(fragment,"chatActivityEnterView");
        Object input=diagnosticField(editorView,"messageEditText");
        CharSequence message=input==null?null:(CharSequence)diagnosticCall(input,"getTextToUse");
        if(!reportNow&&(message==null||message.length()==0))return false;
        diagnosticBoolean("chat_visible",true);
        diagnosticBoolean("composer_present",editorView!=null);
        diagnosticBoolean("editor_present",input!=null);
        if(editorView==null)return true;
        View view=(View)editorView;
        for(String name:new String[]{"destroyed","isPaused","isLiveComment","recordingAudioVideo","richDraftActive","forceShowSendButton"})diagnosticBoolean(name,Boolean.TRUE.equals(diagnosticField(editorView,name)));
        diagnosticBoolean("app_paused",org.telegram.messenger.ApplicationLoader.mainInterfacePaused);
        diagnosticBoolean("attached",view.isAttachedToWindow());diagnosticBoolean("shown",view.isShown());diagnosticBoolean("window_focus",view.hasWindowFocus());
        Object parent=diagnosticField(editorView,"parentFragment"),delegate=diagnosticField(editorView,"delegate");
        diagnosticBoolean("parent_present",parent!=null);diagnosticBoolean("parent_is_last",parent!=null&&Boolean.TRUE.equals(diagnosticCall(parent,"isLastFragment")));
        diagnosticBoolean("topic",parent!=null&&Boolean.TRUE.equals(diagnosticField(parent,"isTopic")));diagnosticBoolean("delegate_present",delegate!=null);
        int account=((Number)diagnosticField(editorView,"currentAccount")).intValue();
        long dialog=((Number)diagnosticField(editorView,"dialog_id")).longValue();
        diagnosticBoolean("selected_account_matches",account==org.telegram.messenger.UserConfig.selectedAccount);
        diagnosticBoolean("owner_present",org.telegram.messenger.CommunityUndoSendSettings.owner(account)!=0);
        diagnosticBoolean("preference_enabled",org.telegram.messenger.CommunityUndoSendSettings.enabled(account));
        diagnosticBoolean("window_is_10_seconds",org.telegram.messenger.CommunityUndoSendSettings.seconds(account)==10);
        diagnosticBoolean("secret_chat",org.telegram.messenger.DialogObject.isEncryptedDialog(dialog));
        diagnosticBoolean("stars_price_nonzero",((Number)diagnosticCall(editorView,"getStarsPrice")).longValue()!=0);
        diagnosticBoolean("schedule_mode",Boolean.TRUE.equals(diagnosticCall(editorView,"isInScheduleMode")));
        for(String name:new String[]{"audioToSend","videoToSendMessageObject","editingMessageObject","replyingMessageObject","replyingQuote","replyingTopMessage","messageSendPreview"})diagnosticBoolean(name+"_present",diagnosticField(editorView,name)!=null);
        diagnosticBoolean("thread_message_present",diagnosticCall(editorView,"getThreadMessage")!=null);
        diagnosticBoolean("monoforum_peer_nonzero",((Number)diagnosticCall(editorView,"getSendMonoForumPeerId")).longValue()!=0);
        diagnosticBoolean("suggestion_present",diagnosticCall(editorView,"getSendMessageSuggestionParams")!=null);
        if(delegate!=null){
            // Resolve interface default methods as well as concrete implementations.
            Class<?> contract=Class.forName("org.telegram.ui.Components.ChatActivityEnterView$ChatActivityEnterViewDelegate");
            diagnosticBoolean("forwarding",Boolean.TRUE.equals(contract.getMethod("hasForwardingMessages").invoke(delegate)));
            diagnosticBoolean("reply_story_present",contract.getMethod("getReplyToStory").invoke(delegate)!=null);
            diagnosticBoolean("delegate_quote_present",contract.getMethod("getReplyQuote").invoke(delegate)!=null);
        }
        diagnosticBoolean("effect_nonzero",((Number)diagnosticField(editorView,"effectId")).longValue()!=0);
        diagnosticBoolean("slow_mode_positive",((Number)diagnosticField(editorView,"slowModeTimer")).longValue()>0);
        diagnosticBoolean("ephemeral",Boolean.TRUE.equals(diagnosticCall(diagnosticField(editorView,"animatorEphemeralMessageVisibility"),"getValue")));
        diagnosticBoolean("message_missing",message==null);diagnosticBoolean("message_empty",message==null||message.length()==0);
        diagnosticBoolean("over_length_limit",message!=null&&message.length()>org.telegram.messenger.MessagesController.getInstance(account).getMaxMessageLength());
        TLRPC.User user=((org.telegram.ui.ChatActivity)fragment).getCurrentUser();diagnosticBoolean("bot",user!=null&&user.bot);
        diagnosticBoolean("command",message!=null&&message.toString().trim().startsWith("/"));diagnosticBoolean("trimmed_empty",message==null||message.toString().trim().isEmpty());
        diagnosticBoolean("spanned",message instanceof android.text.Spanned);
        if(message instanceof android.text.Spanned){android.text.Spanned spans=(android.text.Spanned)message;
            for(android.text.style.CharacterStyle span:spans.getSpans(0,message.length(),android.text.style.CharacterStyle.class)){
                report("UNDO_SPAN type="+span.getClass().getName()+" flags="+spans.getSpanFlags(span));
            }
        }
        java.lang.reflect.Method guard=org.telegram.ui.Components.ChatActivityEnterView.class.getDeclaredMethod("communityUndoContextValid",CharSequence.class,int.class,int.class,long.class);guard.setAccessible(true);
        diagnosticBoolean("context_valid_for_immediate_free_text",Boolean.TRUE.equals(guard.invoke(editorView,message,0,0,0L)));
        return true;
    }
    private TextView findText(View view,String text){
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private AmpInteractiveCell find(View v) {
        if (v instanceof AmpInteractiveCell) try {Field f=AmpInteractiveCell.class.getDeclaredField("block");f.setAccessible(true);AmpInteractiveBlock block=(AmpInteractiveBlock)f.get(v);if(block!=null&&block.url.contains("/"+chart+"/"))return (AmpInteractiveCell)v;} catch(Exception e){throw new RuntimeException(e);}
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
            AmpInteractiveCell c=find(((ViewGroup)v).getChildAt(i)); if(c!=null)return c;
        }
        return null;
    }
    private View nativeGraph(View v){
        if(v instanceof WebView)throw new AssertionError("External WebView found in native chart");
        if(v instanceof AmpNativeChartView||v instanceof AmpNativeMapView||v instanceof AmpNativeTableView)return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View graph=nativeGraph(((ViewGroup)v).getChildAt(i));if(graph!=null)return graph;}
        return null;
    }
    private boolean scrollToChart(View view) {
        try {
            Object adapter=view.getClass().getMethod("getAdapter").invoke(view);
            if(adapter!=null){int count=(Integer)adapter.getClass().getMethod("getItemCount").invoke(adapter);
                for(int i=0;i<Math.min(2000,count);i++)if((Integer)adapter.getClass().getMethod("getItemViewType",int.class).invoke(adapter,i)==93){
                    Field pad=adapter.getClass().getDeclaredField("padding");pad.setAccessible(true);
                    Object block=adapter.getClass().getMethod("getItem",int.class).invoke(adapter,i-(pad.getBoolean(adapter)?1:0));
                    if(!(block instanceof AmpInteractiveBlock)||!((AmpInteractiveBlock)block).url.contains("/"+chart+"/"))continue;
                    report("ARTICLE_CHART: "+((AmpInteractiveBlock)block).url);
                    Object layout=view.getClass().getMethod("getLayoutManager").invoke(view);
                    layout.getClass().getMethod("scrollToPositionWithOffset",int.class,int.class).invoke(layout,i,0);
                    return true;
                }
            }
        } catch(NoSuchMethodException ignored) {} catch(Exception e){throw new RuntimeException(e);}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(scrollToChart(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    private void testMapTouch(AmpNativeMapView map)throws Exception {
        Field dataField=AmpNativeMapView.class.getDeclaredField("data");dataField.setAccessible(true);AmpMapData data=(AmpMapData)dataField.get(map);
        int target=0;for(int i=0;i<data.regions.size();i++)if(data.regions.get(i).name.equals("Madrid")){target=i;break;}
        double x=-1,y=-1;
        outer:for(int iy=0;iy<300;iy++)for(int ix=0;ix<300;ix++){double xx=(ix+.5)*data.width/300,yy=(iy+.5)*data.height/300;if(data.hit(xx,yy)==target){x=xx;y=yy;break outer;}}
        if(x<0)throw new AssertionError("No touch target");
        java.lang.reflect.Method base=AmpNativeMapView.class.getDeclaredMethod("base"),ox=AmpNativeMapView.class.getDeclaredMethod("originX"),oy=AmpNativeMapView.class.getDeclaredMethod("originY");base.setAccessible(true);ox.setAccessible(true);oy.setAccessible(true);
        float scale=(Float)base.invoke(map);final float px=(Float)ox.invoke(map)+(float)x*scale,py=(Float)oy.invoke(map)+(float)y*scale;
        runOnMainSync(()->{long t=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(t,t,0,px,py,0),up=android.view.MotionEvent.obtain(t,t+40,1,px,py,0);map.dispatchTouchEvent(down);map.dispatchTouchEvent(up);down.recycle();up.recycle();});
        if(!map.getContentDescription().toString().startsWith(data.regions.get(target).name+" ·"))throw new AssertionError("Map hit test failed");
        report("MAP_TOUCH: "+map.getContentDescription());
        runOnMainSync(()->map.zoom(2));
        Field pan=AmpNativeMapView.class.getDeclaredField("panX");pan.setAccessible(true);float before=pan.getFloat(map);
        runOnMainSync(()->{long t=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(t,t,0,100,100,0),move=android.view.MotionEvent.obtain(t,t+60,2,170,130,0),up=android.view.MotionEvent.obtain(t,t+100,1,170,130,0);map.dispatchTouchEvent(down);map.dispatchTouchEvent(move);map.dispatchTouchEvent(up);down.recycle();move.recycle();up.recycle();});
        if(pan.getFloat(map)==before)throw new AssertionError("Map pan failed");
        report("MAP_PAN: "+before+" -> "+pan.getFloat(map));runOnMainSync(map::reset);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if(ampTlsProbeOnly){
                try{tlsProbeMatrix();result.putString("stream","TLS_PROBE_MATRIX_COMPLETE=true\n");finish(Activity.RESULT_OK,result);}
                catch(Exception failure){reportAmpTransportFailure(failure);result.putString("stream","TLS_PROBE_MATRIX_COMPLETE=false\n");finish(Activity.RESULT_CANCELED,result);}
                return;
            }
            if(ampDiscoveryOnly){
                if(!diagnosticArticleProvided||!AmpUrls.isEligible(article)){result.putString("stream","AMP_DISCOVERY_INPUT_VALID=false\n");finish(Activity.RESULT_CANCELED,result);return;}
                CountDownLatch completed=new CountDownLatch(1);AtomicReference<AmpResolver.Result> discovered=new AtomicReference<>();
                AmpResolver.discover(article,value->{discovered.set(value);completed.countDown();});
                boolean received=completed.await(35,TimeUnit.SECONDS);AmpResolver.Result value=discovered.get();
                report("AMP_DISCOVERY completed="+received+" available="+(value!=null)+" readerAllowed="+(value!=null&&value.readerAllowed));
                if(value!=null){
                    String provider="Origen".equals(value.provider)?"origin":"Google".equals(value.provider)?"google":"Bing".equals(value.provider)?"bing":"other";
                    report("AMP_DISCOVERY provider="+provider+" html_present="+(value.html!=null&&!value.html.isEmpty())+" language_metadata_present="+(value.languageHtml!=null&&!value.languageHtml.isEmpty()));
                }else if(received){
                    try{
                        String origin=AmpResolver.fetchPublicText(article);
                        report("AMP_ORIGIN fetched=true amp_document="+AmpResolver.isAmpDocument(origin)+" amp_alternate_declared="+(AmpResolver.discoverAmpUrl(article,origin)!=null)+" reader_allowed="+AmpResolver.isAmpHtml(origin));
                    }catch(Exception failure){report("AMP_ORIGIN fetched=false failure_type="+failure.getClass().getSimpleName());reportAmpTransportFailure(failure);}
                }
                result.putString("stream","AMP_DISCOVERY_DIAGNOSTIC_COMPLETE="+received+"\n");finish(received?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;
            }
            // Instrumentation waits for the exact component; launcher aliases can leave it waiting forever.
            Intent intent=new Intent(getTargetContext(), org.telegram.ui.LaunchActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityMonitor monitor=addMonitor(org.telegram.ui.LaunchActivity.class.getName(),null,false);
            Activity activity;
            try {
                getTargetContext().startActivity(intent);
                activity=monitor.waitForActivityWithTimeout(15000);
            } finally { removeMonitor(monitor); }
            if(activity==null)throw new AssertionError("LaunchActivity did not resume within 15 seconds");
            Thread.sleep(2500);

            if(backendAccountsOnly){
                try{BackendAccountsDeviceChecks.run(this,activity);result.putString("stream","BACKEND_ACCOUNTS_COMPLETE=true\n");finish(Activity.RESULT_OK,result);}
                catch(Throwable failure){report("BACKEND_ACCOUNTS_ERROR type="+failure.getClass().getSimpleName());result.putString("stream","BACKEND_ACCOUNTS_COMPLETE=false\n");finish(Activity.RESULT_CANCELED,result);}
                return;
            }
            if(photoAmpOnly){
                try{photoAmpFixture(activity);result.putString("stream","PHOTO_AMP_COMPLETE=true\n");finish(Activity.RESULT_OK,result);}
                catch(Throwable failure){report("PHOTO_AMP_ERROR stage="+photoAmpStage+" type="+failure.getClass().getSimpleName());result.putString("stream","PHOTO_AMP_COMPLETE=false\n");finish(Activity.RESULT_CANCELED,result);}
                return;
            }
            if(communityUndoDiagnoseOnly){
                AtomicReference<Throwable> failure=new AtomicReference<>();java.util.concurrent.atomic.AtomicBoolean inspected=new java.util.concurrent.atomic.AtomicBoolean();
                long deadline=android.os.SystemClock.elapsedRealtime()+Math.max(1,hold)*1000L;
                while(!inspected.get()&&failure.get()==null&&android.os.SystemClock.elapsedRealtime()<deadline){
                    runOnMainSync(()->{try{inspected.set(diagnoseCommunityUndo(false));}catch(Throwable t){failure.set(t);}});
                    if(!inspected.get()&&failure.get()==null)Thread.sleep(500);
                }
                if(failure.get()==null&&!inspected.get())runOnMainSync(()->{try{diagnoseCommunityUndo(true);}catch(Throwable t){failure.set(t);}});
                // Intentionally sanitize failures: exception messages can contain user-controlled values.
                if(failure.get()!=null){report("UNDO_DIAGNOSTIC_ERROR type="+failure.get().getClass().getSimpleName());result.putString("stream","UNDO_DIAGNOSTIC_COMPLETE=false\n");finish(Activity.RESULT_CANCELED,result);return;}
                result.putString("stream","UNDO_DIAGNOSTIC_COMPLETE=true\n");finish(Activity.RESULT_OK,result);return;
            }
            if(communityToolsOnly){
                java.util.concurrent.atomic.AtomicInteger fakeSends=new java.util.concurrent.atomic.AtomicInteger();
                CountDownLatch fired=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
                runOnMainSync(()->{
                    org.telegram.messenger.UndoSendDelay delayed=new org.telegram.messenger.UndoSendDelay(android.os.SystemClock::elapsedRealtime,new org.telegram.messenger.UndoSendDelay.Scheduler(){
                        public void post(Runnable task,long delay){org.telegram.messenger.AndroidUtilities.runOnUIThread(task,delay);}
                        public void remove(Runnable task){org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(task);}
                    });
                    delayed.start(3000,()->true,()->{fakeSends.addAndGet(100);},()->{});delayed.cancel();
                    delayed.start(3000,()->true,()->{fakeSends.incrementAndGet();fired.countDown();},()->{});
                });
                if(!fired.await(5,TimeUnit.SECONDS)||fakeSends.get()!=1)throw new AssertionError("Delayed callback/cancel failed");
                int[] titles={org.telegram.messenger.R.string.CommunityUndoSend,org.telegram.messenger.R.string.CommunityDraftHistory,org.telegram.messenger.R.string.CommunityPendingReplies};
                Class<?>[] screens={org.telegram.ui.CommunityUndoSendActivity.class,org.telegram.ui.CommunityDraftHistoryActivity.class,org.telegram.ui.CommunityPendingRepliesActivity.class};
                org.telegram.ui.CommunitySettingsActivity settings=new org.telegram.ui.CommunitySettingsActivity(org.telegram.ui.CommunitySettingsActivity.MESSAGING);
                runOnMainSync(()->{try{activity.getClass().getMethod("presentFragment",org.telegram.ui.ActionBar.BaseFragment.class,boolean.class,boolean.class).invoke(activity,settings,false,true);}catch(Throwable t){failure.set(t);}});Thread.sleep(800);if(failure.get()!=null)throw new AssertionError(failure.get());
                for(int i=0;i<titles.length;i++){
                    final int title=titles[i];
                    runOnMainSync(()->{try{TextView entry=findText(settings.getFragmentView(),org.telegram.messenger.LocaleController.getString(title));if(entry==null)throw new AssertionError("Settings entry missing");entry.performClick();}catch(Throwable t){failure.set(t);}});
                    if(failure.get()!=null)throw new AssertionError(failure.get());Thread.sleep(800);
                    // Tablet settings live in the layers stack, not LaunchActivity's main stack.
                    if(!screens[i].isInstance(settings.getParentLayout().getLastFragment()))throw new AssertionError("Wrong settings screen "+i);
                    report("COMMUNITY_SCREEN_OK: "+screens[i].getSimpleName());
                    runOnMainSync(()->settings.getParentLayout().getLastFragment().finishFragment(false));Thread.sleep(600);
                }
                android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();
                if(shot!=null){java.io.File target=new java.io.File(getTargetContext().getCacheDir(),"community-messaging.png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(target)){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}report("SCREENSHOT: "+target);}
                result.putString("stream","Community tools passed: three settings screens and cancel/deadline on Android scheduler. Fake callbacks only; no messages sent or user drafts changed. API="+android.os.Build.VERSION.SDK_INT+"\n");finish(Activity.RESULT_OK,result);return;
            }
            if(performanceOnly){
                AtomicReference<Throwable> failure=new AtomicReference<>();
                runOnMainSync(()->{try {
                    android.text.TextPaint paint=new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setTextSize(18);
                    org.telegram.ui.Components.SingleLineTextCache cache=new org.telegram.ui.Components.SingleLineTextCache();
                    String source="Comunidad Telebots: vista previa y noticias";
                    for(int size:new int[]{18,24,32}){paint.setTextSize(size);for(int width:new int[]{80,160,260}){
                        String expected=android.text.TextUtils.ellipsize(source,paint,width,android.text.TextUtils.TruncateAt.END).toString();
                        for(int frame=0;frame<100;frame++)if(!expected.equals(cache.get(source,paint,width,false)))throw new AssertionError("Text cache mismatch");
                        if(Math.abs(cache.measuredWidth()-paint.measureText(expected))>.01)throw new AssertionError("Wrong measured width");
                    }}
                    AmpChartData percentage=new AmpChartData(AmpChartData.Kind.AREA,"percentage",java.util.Arrays.asList(java.util.Arrays.asList("year","a","b"),java.util.Arrays.asList("2025","1000","3000"),java.util.Arrays.asList("2026","2000","4000")));
                    percentage.proportional=true;percentage.stacked=false;
                    AmpNativeChartView percentageView=new AmpNativeChartView(activity,percentage,text->{});
                    java.lang.reflect.Field minimum=AmpNativeChartView.class.getDeclaredField("min"),maximum=AmpNativeChartView.class.getDeclaredField("max");minimum.setAccessible(true);maximum.setAccessible(true);
                    if(minimum.getDouble(percentageView)!=0||maximum.getDouble(percentageView)!=100)throw new AssertionError("Percentage axis mismatch");
                    report("PERFORMANCE_TEXT_AND_AXIS_OK");
                    org.telegram.ui.Components.VideoPlayer video=new org.telegram.ui.Components.VideoPlayer(false,false);
                    try {
                        java.lang.reflect.Method ensure=video.getClass().getDeclaredMethod("ensurePlayerCreated");ensure.setAccessible(true);
                        java.lang.reflect.Field player=video.getClass().getDeclaredField("player");player.setAccessible(true);
                        ensure.invoke(video);report("VIDEO_CREATED");Object first=player.get(video);ensure.invoke(video);report("VIDEO_REUSED");
                        if(first==null||first!=player.get(video))throw new AssertionError("Video player not reused");
                        java.lang.reflect.Field mixed=video.getClass().getDeclaredField("mixedAudio");mixed.setAccessible(true);mixed.setBoolean(video,true);
                        ensure.invoke(video);java.lang.reflect.Field audio=video.getClass().getDeclaredField("audioPlayer");audio.setAccessible(true);
                        Object firstAudio=audio.get(video);ensure.invoke(video);
                        if(firstAudio==null||firstAudio!=audio.get(video)||first!=player.get(video))throw new AssertionError("Mixed audio not created/reused");
                    } finally {video.releasePlayer(false);}
                } catch(Throwable t){failure.set(t);}});
                if(failure.get()!=null)throw new AssertionError(failure.get());
                report("PERFORMANCE_OK: actual Android text metrics at 3 sizes/widths; player and mixed audio creation/reuse; API="+android.os.Build.VERSION.SDK_INT);
                result.putString("stream","Performance functional smoke passed; no FPS or streaming claim.\n");finish(Activity.RESULT_OK,result);return;
            }
            if(languageOnly){
                android.content.SharedPreferences preferences=org.telegram.messenger.MessagesController.getGlobalMainSettings();
                String previous=preferences.getString("amp_preferred_language",null);
                try {
                    preferences.edit().putString("amp_preferred_language","es").commit();
                    TLRPC.TL_webPage page=new TLRPC.TL_webPage();page.cached_page=new org.telegram.tgnet.tl.TL_iv.TL_page();
                    String url="https://news.publisher.org/en/story";
                    AmpResolver.Result sample=new AmpResolver.Result(url,url,url,"<html lang='en'><link rel='alternate' hreflang='es' href='/es/noticia'>","Test fixture");
                    runOnMainSync(()->AmpArticleReader.addLanguageOption(page,sample));
                    org.telegram.tgnet.tl.TL_iv.pageBlockParagraph paragraph=(org.telegram.tgnet.tl.TL_iv.pageBlockParagraph)page.cached_page.blocks.get(0);
                    org.telegram.tgnet.tl.TL_iv.textUrl link=(org.telegram.tgnet.tl.TL_iv.textUrl)paragraph.text;
                    if(!"https://news.publisher.org/es/noticia".equals(link.url))throw new AssertionError("Wrong alternate URL");
                    preferences.edit().putString("amp_preferred_language","off").commit();
                    runOnMainSync(()->AmpArticleReader.addLanguageOption(page,sample));
                    if(page.cached_page.blocks.size()!=1)throw new AssertionError("Disabled language option still inserted");
                    report("LANGUAGE_OK: native article link English -> Spanish, disabled preference respected, API="+android.os.Build.VERSION.SDK_INT);
                } finally {if(previous==null)preferences.edit().remove("amp_preferred_language").commit();else preferences.edit().putString("amp_preferred_language",previous).commit();}
                result.putString("stream","Language integration test passed (controlled fixture).\n");finish(Activity.RESULT_OK,result);return;
            }
            final String html=AmpResolver.fetchPublicText(article);
            report("REAL_ARTICLE: "+article+" bytes="+html.length());
            CountDownLatch parsed=new CountDownLatch(1); AtomicReference<Throwable> error=new AtomicReference<>();
            ArticleViewer viewer=ArticleViewer.getInstance();
            runOnMainSync(()->{
                WebInstantView parser=new WebInstantView();
                parser.readHTML(article,new ByteArrayInputStream(AmpArticleReader.normalizeHtml(html).getBytes(java.nio.charset.StandardCharsets.UTF_8)), json->{
                    try {
                        if(json==null)throw new AssertionError("Parser returned null");
                        AmpArticleReader.normalizeLinks(json,article,0,new int[]{20000});
                        TLRPC.TL_webPage page=parser.parseJSON(article,json);
                        WebInstantView.instants.put(page,parser);
                        viewer.setParentActivity(activity,null);
                        if(!viewer.open(page,page.url))throw new AssertionError("Viewer refused page");
                    } catch(Throwable t){error.set(t);} finally {parsed.countDown();}
                });
            });
            if(!parsed.await(25,TimeUnit.SECONDS))throw new AssertionError("Parser timed out");
            if(error.get()!=null)throw new AssertionError(error.get());
            Thread.sleep(2000);
            Field rootField=ArticleViewer.class.getDeclaredField("windowView");rootField.setAccessible(true);
            runOnMainSync(()->{try{scrollToChart((View)rootField.get(viewer));}catch(Exception e){error.set(e);}});
            Thread.sleep(1500);
            AtomicReference<AmpInteractiveCell> cell=new AtomicReference<>();
            runOnMainSync(()->{try{cell.set(find((View)rootField.get(viewer)));}catch(Exception e){error.set(e);}});
            if(cell.get()==null)throw new AssertionError("No interactive card");
            Field contentField=AmpInteractiveCell.class.getDeclaredField("content");contentField.setAccessible(true);
            if(contentField.get(cell.get())!=null)throw new AssertionError("Data loaded before tap");
            report("CARD_READY: native chart not loaded before tap; capture now");
            Thread.sleep(10000);
            runOnMainSync(()->cell.get().getChildAt(1).performClick());
            for(int attempt=0;attempt<25&&contentField.get(cell.get())==null;attempt++)Thread.sleep(2000);
            if(contentField.get(cell.get())==null){
                try{Field blockField=AmpInteractiveCell.class.getDeclaredField("block");blockField.setAccessible(true);AmpChartLoader.load(((AmpInteractiveBlock)blockField.get(cell.get())).url);}catch(Throwable t){report("DATA_ERROR: "+t+" cause="+t.getCause());}
                throw new AssertionError("Native data failed to load");
            }
            AtomicReference<View> graph=new AtomicReference<>();
            runOnMainSync(()->graph.set(nativeGraph(cell.get())));
            if(graph.get()==null)throw new AssertionError("No native chart view");
            String before=graph.get().getContentDescription().toString();
            runOnMainSync(()->{if(graph.get() instanceof AmpNativeMapView)((AmpNativeMapView)graph.get()).step(1);else if(graph.get() instanceof AmpNativeTableView)((AmpNativeTableView)graph.get()).step(1);else ((AmpNativeChartView)graph.get()).step(1);});
            String after=graph.get().getContentDescription().toString();
            if(before.equals(after))throw new AssertionError("Selection did not change");
            report("NATIVE_SELECTION: "+before+" -> "+after);
            if(graph.get() instanceof AmpNativeMapView){
                AmpNativeMapView map=(AmpNativeMapView)graph.get();
                runOnMainSync(()->map.zoom(2));if(map.zoomLevel()!=2)throw new AssertionError("Map zoom failed");
                runOnMainSync(map::reset);if(map.zoomLevel()!=1)throw new AssertionError("Map reset failed");
                report("MAP_ZOOM: 1 -> 2 -> 1; native region selection verified");
                testMapTouch(map);
            }
            report("NATIVE_READY: Canvas chart, no WebView. Capture and touch now.");
            if(graph.get() instanceof AmpNativeMapView)runOnMainSync(()->{
                int[] location=new int[2];graph.get().getLocationOnScreen(location);
                int delta=location[1]-getTargetContext().getResources().getDisplayMetrics().heightPixels/4;
                android.view.ViewParent parent=graph.get().getParent();
                while(parent instanceof View){boolean list=false;for(Class<?> type=parent.getClass();type!=null;type=type.getSuperclass())if(type.getName().contains("RecyclerListView"))list=true;if(list){try{parent.getClass().getMethod("scrollBy",int.class,int.class).invoke(parent,0,Math.max(0,delta));}catch(Exception e){throw new RuntimeException(e);}break;}parent=parent.getParent();}
            });
            Thread.sleep(1000);
            android.graphics.Bitmap capture=getUiAutomation().takeScreenshot();
            if(capture==null)throw new AssertionError("No device screenshot");
            java.io.File directory=getTargetContext().getExternalFilesDir(null);
            if(directory==null)directory=getTargetContext().getCacheDir();
            java.io.File screenshot=new java.io.File(directory,"amp-"+chart+".png");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(screenshot)){capture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{capture.recycle();}
            report("SCREENSHOT: "+screenshot.getAbsolutePath());
            Thread.sleep(hold*1000L);
            result.putString("stream","AMP device run complete; real news parsed, native chart loaded and selection verified.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable t) { java.io.StringWriter trace=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","FAIL: "+trace.toString()+"\n"); finish(Activity.RESULT_CANCELED,result); }
    }
}
