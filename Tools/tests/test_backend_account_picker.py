"""Compile the actual ProfileActivity chooser against small offline UI/session mocks."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / 'TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java').read_text(encoding='utf-8')
method = source[source.index('    private void showBackendAccounts() {'):source.index('    private void updateRowsIds() {')]
keys = sorted(set(re.findall(r'R.string.(\w+)', method)))
resources = ''.join('static final int ' + key + '=' + str(i) + ';' for i, key in enumerate(keys))
code = r'''import java.util.*;
public class BackendChooserTest {
 static class R {static class string {KEYS}}
 static String getString(int id){return "label";}
 static class UserConfig {
  static final int MAX_ACCOUNT_COUNT=4,MAX_ACCOUNT_DEFAULT_COUNT=3;
  static UserConfig[] all={new UserConfig(),new UserConfig(),new UserConfig(),new UserConfig()};
  static boolean premium;long uid;
  static UserConfig getInstance(int a){return all[a];}boolean isClientActivated(){return uid!=0;}
  long getClientUserId(){return uid;}Object getCurrentUser(){return this;}
  static boolean hasPremiumOnAccounts(){return premium;}
  static int getActivatedAccountsCount(){int n=0;for(UserConfig u:all)if(u.isClientActivated())n++;return n;}
 }
 static class ConnectionsManager {static ConnectionsManager[] all={new ConnectionsManager(),new ConnectionsManager(),new ConnectionsManager(),new ConnectionsManager()};boolean test;static ConnectionsManager getInstance(int a){return all[a];}boolean isTestBackend(){return test;}}
 static class UserObject {static String getUserName(Object user){return "user";}}
 static class LaunchActivity {int selected=-1;void switchToAccount(int a,boolean remove){selected=a;}}
 static class LoginActivity {int slot;boolean test;LoginActivity(int a,boolean t){slot=a;test=t;}}
 interface Click {void run(Object dialog,int which);}
 static class AlertDialog {static class Builder {Click click;CharSequence[] items;Builder(Object a,Object b){}void setTitle(String s){}void setMessage(String s){}void setItems(CharSequence[] a,Click c){items=a;click=c;}void setNegativeButton(String s,Object n){}void setPositiveButton(String s,Object n){}Builder create(){return this;}}}
 int currentAccount=0;Object resourcesProvider;LaunchActivity activity=new LaunchActivity();AlertDialog.Builder last;LoginActivity login;
 Object getParentActivity(){return activity;}ConnectionsManager getConnectionsManager(){return ConnectionsManager.getInstance(currentAccount);}void showDialog(AlertDialog.Builder b){last=b;}void presentFragment(LoginActivity a){login=a;}
 METHOD
 static void ck(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[]args){
  BackendChooserTest p=new BackendChooserTest();UserConfig.all[0].uid=10;UserConfig.all[1].uid=20;ConnectionsManager.all[1].test=true;
  p.showBackendAccounts();ck(p.last.items.length==2,"opposite backend only");p.last.click.run(null,0);ck(p.activity.selected==1&&UserConfig.all[0].uid==10,"existing selection preserves source");
  p.activity.selected=-1;p.showBackendAccounts();UserConfig.all[1].uid=21;p.last.click.run(null,0);ck(p.activity.selected==-1,"replaced owner rejected");
  p.showBackendAccounts();ConnectionsManager.all[1].test=false;p.last.click.run(null,0);ck(p.activity.selected==-1,"changed backend rejected");ConnectionsManager.all[1].test=true;
  p.showBackendAccounts();p.last.click.run(null,1);ck(p.login.slot==2&&p.login.test&&UserConfig.all[0].uid==10,"new target uses empty slot");
  p.login=null;p.showBackendAccounts();UserConfig.all[2].uid=30;p.last.click.run(null,1);ck(p.login==null,"default allowance enforced despite free fourth slot");
  UserConfig.premium=true;p.showBackendAccounts();p.last.click.run(null,1);ck(p.login!=null&&p.login.slot==3,"premium allowance permits free slot");
  p.login=null;p.showBackendAccounts();UserConfig.premium=false;p.last.click.run(null,1);ck(p.login==null,"premium change rechecked at selection");
  UserConfig.premium=true;p.showBackendAccounts();UserConfig.all[3].uid=40;p.last.click.run(null,1);ck(p.login==null&&UserConfig.all[0].uid==10,"newly full slots preserve sessions");
  p.currentAccount=1;p.showBackendAccounts();ck(p.last.items.length==4,"production accounts visible from test backend");p.last.click.run(null,0);ck(p.activity.selected==0,"return to production");
  System.out.println("PASS actual chooser: backend/owner revalidation, new slot, standard/premium allowance, full slots and preserved sessions");
 }
}'''.replace('KEYS', resources).replace('METHOD', method)
with tempfile.TemporaryDirectory() as directory:
    file = Path(directory, 'BackendChooserTest.java')
    file.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, str(file)], check=True)
    subprocess.run(['java', '-cp', directory, 'BackendChooserTest'], check=True)
