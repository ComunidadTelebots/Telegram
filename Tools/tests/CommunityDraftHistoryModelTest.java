import org.telegram.messenger.CommunityDraftHistoryModel;
import org.telegram.messenger.CommunityDraftHistoryModel.Entry;
import java.util.*;
public class CommunityDraftHistoryModelTest {
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  long now=1000000000L;
  check(CommunityDraftHistoryModel.eligible(123,false,true,0),"Known ordinary chat");
  check(!CommunityDraftHistoryModel.eligible(123,true,true,0),"Secret excluded");
  check(!CommunityDraftHistoryModel.eligible(123,false,false,0),"Unknown privacy excluded");
  check(!CommunityDraftHistoryModel.eligible(123,false,true,60),"TTL excluded");
  check(!CommunityDraftHistoryModel.eligible(0,false,true,0),"No target excluded");
  check(CommunityDraftHistoryModel.privacy(123,false,false,0)==CommunityDraftHistoryModel.UNKNOWN,"Cold cache is unknown, not forbidden");
  check(CommunityDraftHistoryModel.privacy(123,true,false,-1)==CommunityDraftHistoryModel.DISALLOWED,"Secret remains forbidden even without full info");
  check(CommunityDraftHistoryModel.privacy(123,false,true,60)==CommunityDraftHistoryModel.DISALLOWED,"Known TTL is forbidden");
  check(CommunityDraftHistoryModel.privacy(123,false,true,0)==CommunityDraftHistoryModel.ALLOWED,"Known disabled TTL is allowed");
  ArrayList<Entry> entries=new ArrayList<>();
  entries=CommunityDraftHistoryModel.record(entries,1,0,"first",now);
  entries=CommunityDraftHistoryModel.record(entries,1,0,"first",now+1);check(entries.size()==1,"Unchanged draft not duplicated");
  entries=CommunityDraftHistoryModel.record(entries,1,0,"second",now+2);
  entries=CommunityDraftHistoryModel.record(entries,1,0,"first",now+3);check(entries.size()==3&&entries.get(0).text.equals("first"),"Revisited version preserved");
  entries=CommunityDraftHistoryModel.record(entries,1,0,"",now+4);check(entries.size()==3,"Empty text not captured");
  entries=CommunityDraftHistoryModel.record(entries,1,0,new String(new char[4097]).replace('\0','x'),now+4);check(entries.size()==3,"Oversized text rejected");
  for(int i=0;i<20;i++)entries=CommunityDraftHistoryModel.record(entries,1,0,"v"+i,now+5+i);
  check(entries.size()==8&&entries.get(0).text.equals("v19"),"Bounded peer history retains latest");
  entries=CommunityDraftHistoryModel.record(entries,1,9,"topic",now+30);
  check(entries.size()==9,"Topics isolated");
  entries=CommunityDraftHistoryModel.removePeer(entries,1,0,false);check(entries.size()==1&&entries.get(0).threadId==9,"Remote clearing removes only exact thread");
  entries=CommunityDraftHistoryModel.removePeer(entries,1,0,true);check(entries.isEmpty(),"TTL change removes all peer threads");
  for(int i=0;i<70;i++)entries=CommunityDraftHistoryModel.record(entries,100+i,0,"draft"+i,now+i);
  check(entries.size()==50&&entries.get(0).text.equals("draft69"),"Global version cap");
  check(CommunityDraftHistoryModel.prune(entries,now+CommunityDraftHistoryModel.MAX_AGE_MS+100).isEmpty(),"Retention expired");
  entries=new ArrayList<>();entries.add(new Entry("id",1,0,now+1,"future"));check(CommunityDraftHistoryModel.prune(entries,now).isEmpty(),"Future clock data rejected");
  entries=new ArrayList<>();entries.add(new Entry("id",1,0,now,"text"));entries.add(entries.get(0));check(CommunityDraftHistoryModel.prune(entries,now).size()==1,"Duplicate stored IDs rejected");
  entries=CommunityDraftHistoryModel.record(new ArrayList<>(),1,0,"a",now);entries=CommunityDraftHistoryModel.record(entries,1,0,"b",now);check(entries.get(0).text.equals("b"),"Same-clock capture order");
  System.out.println("CommunityDraftHistoryModelTest: 22 privacy, versioning, deletion and quota checks passed");
 }
}
