"""Execute production account reconciliation with fake Android services (no device data)."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/ContactsController.java").read_text(encoding="utf-8")
start = source.index("    public void checkAppAccount() {")
end = source.index("    public void deleteUnknownAppAccounts()", start)
methods = source[start:end]
harness = r'''import java.util.*;
public class ContactsController {
 final int currentAccount; Account systemAccount; int reads;
 static ContactsController[] instances = new ContactsController[4];
 ContactsController(int account) {currentAccount=account;}
 static ContactsController getInstance(int n) {if(instances[n]==null)instances[n]=new ContactsController(n);return instances[n];}
 UserConfig getUserConfig(){return UserConfig.getInstance(currentAccount);}
 void readContacts(){reads++;}
 static class Account {final String name,type;Account(String n,String t){name=n;type=t;}}
 static class TLRPC {static class User {long id;User(long n){id=n;}}}
 static class UserConfig {
  static final int MAX_ACCOUNT_COUNT=4; static UserConfig[] users=new UserConfig[4];
  TLRPC.User user; static UserConfig getInstance(int n){if(users[n]==null)users[n]=new UserConfig();return users[n];}
  TLRPC.User getCurrentUser(){return user;}boolean isClientActivated(){return user!=null;}long getClientUserId(){return user.id;}
 }
 static class ApplicationLoader {static Object applicationContext;static String getApplicationId(){return "test.app";}}
 static class Utilities {static Queue globalQueue=new Queue();static class Queue {
  List<Runnable> work=new ArrayList<>();void postRunnable(Runnable r){work.add(r);}void drain(){for(Runnable r:new ArrayList<>(work))r.run();work.clear();}
 }}
 static class AccountManager {
  static AccountManager instance;List<Account> accounts=new ArrayList<>();int queries,adds,removes;boolean fail;
  static AccountManager get(Object context){return instance;}
  Account[] getAccountsByType(String type){queries++;if(fail)throw new SecurityException();return accounts.toArray(new Account[0]);}
  void removeAccount(Account a,Object b,Object c){removes++;accounts.remove(a);}
  void addAccountExplicitly(Account a,String b,Object c){adds++;accounts.add(a);}
 }
 static void setup(long... ids){instances=new ContactsController[4];UserConfig.users=new UserConfig[4];AccountManager.instance=new AccountManager();Utilities.globalQueue=new Utilities.Queue();for(int i=0;i<ids.length;i++)if(ids[i]!=0)UserConfig.getInstance(i).user=new TLRPC.User(ids[i]);}
 static Account existing(long id){Account a=new Account(Long.toString(id),"test.app");AccountManager.instance.accounts.add(a);return a;}
 static void verify(boolean result,String message){if(!result)throw new AssertionError(message);}
 public static void main(String[] args){
  setup(11,0,33,0);Account first=existing(11);existing(99);
  checkAllAppAccounts();verify(AccountManager.instance.queries==0,"Must remain asynchronous");verify(Utilities.globalQueue.work.size()==1,"One startup task");Utilities.globalQueue.drain();
  verify(AccountManager.instance.queries==1,"Single Binder query");verify(AccountManager.instance.removes==1,"Remove obsolete account once");verify(AccountManager.instance.adds==1,"Create only missing active account");
  verify(getInstance(0).systemAccount==first&&getInstance(2).systemAccount.name.equals("33"),"Correct account assignment");
  verify(getInstance(0).reads==1&&getInstance(2).reads==1&&getInstance(1).reads==0&&getInstance(3).reads==0,"Contacts for active accounts only");
  setup(11,22,33,44);for(long id:new long[]{11,22,33,44})existing(id);checkAllAppAccounts();Utilities.globalQueue.drain();
  verify(AccountManager.instance.queries==1&&AccountManager.instance.adds==0&&AccountManager.instance.removes==0,"Existing multi-account state");for(int i=0;i<4;i++)verify(getInstance(i).reads==1,"Every active account checked");
  setup();existing(99);checkAllAppAccounts();Utilities.globalQueue.drain();verify(AccountManager.instance.removes==1&&AccountManager.instance.adds==0,"Cleanup while logged out");
  setup(11,22);Account other=existing(22);getInstance(1).systemAccount=other;getInstance(0).checkAppAccount();Utilities.globalQueue.drain();
  verify(getInstance(1).systemAccount==other&&getInstance(1).reads==0&&AccountManager.instance.removes==0,"Individual login preserves other account");
  setup(11);AccountManager.instance.fail=true;checkAllAppAccounts();Utilities.globalQueue.drain();verify(getInstance(0).reads==1&&AccountManager.instance.adds==1,"Original account-query failure recovery");
  System.out.println("Startup reconciliation: active/missing/stale/multi-account/logged-out/single-account/failure cases passed; 4 -> 1 service queries");
 }
'''
with tempfile.TemporaryDirectory() as temp:
    java = Path(temp, "ContactsController.java")
    java.write_text(harness + methods + "}\n", encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temp, str(java)], check=True)
    subprocess.run(["java", "-cp", temp, "ContactsController"], check=True)
