"""Deterministic cancellation/deadline/account races; no Android, network or real messages."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp,'UndoDelayTest.java');p.write_text(r'''import org.telegram.messenger.UndoSendDelay;
public class UndoDelayTest {
 static class Fake implements UndoSendDelay.Clock,UndoSendDelay.Scheduler {
  long now,postedDelay;Runnable scheduled;int removed;
  public long now(){return now;}public void post(Runnable r,long delay){scheduled=r;postedDelay=delay;}public void remove(Runnable r){if(scheduled==r)scheduled=null;removed++;}
 }
 static void check(boolean valid,String why){if(!valid)throw new AssertionError(why);}
 public static void main(String[] args){
  for(long window:new long[]{3000,5000,10000}){
   Fake f=new Fake();UndoSendDelay gate=new UndoSendDelay(f,f);int[] sent={0},cancelled={0};
   gate.start(window,()->true,()->sent[0]++,()->cancelled[0]++);Runnable original=f.scheduled;
   check(sent[0]==0&&gate.isPending()&&f.postedDelay==window,"Premature send");
   f.now=window-1;original.run();check(sent[0]==0&&f.postedDelay==1,"Early callback sent");
   f.now=window;original.run();original.run();check(sent[0]==1&&!gate.isPending()&&cancelled[0]==0,"Duplicate dispatch");
  }
  Fake f=new Fake();UndoSendDelay gate=new UndoSendDelay(f,f);int[] sent={0},cancelled={0};
  gate.start(5000,()->true,()->sent[0]++,()->cancelled[0]++);Runnable cancelledRace=f.scheduled;
  gate.cancel();gate.cancel();f.now=5000;cancelledRace.run();check(sent[0]==0&&cancelled[0]==1,"Cancel race");
  gate.start(5000,()->true,()->sent[0]++,()->cancelled[0]++);Runnable old=f.scheduled;
  gate.start(3000,()->true,()->sent[0]++,()->cancelled[0]++);old.run();check(sent[0]==0&&gate.isPending(),"Replaced task sent");
  gate.cancel();
  boolean[] active={true};gate.start(5000,()->active[0],()->sent[0]++,()->cancelled[0]++);active[0]=false;f.now+=5000;f.scheduled.run();check(sent[0]==0&&!gate.isPending(),"Invalid account/context sent");
  long[] sender={123};gate.start(5000,()->sender[0]==123,()->sent[0]++,()->cancelled[0]++);sender[0]=-456;f.now+=5000;f.scheduled.run();check(sent[0]==0&&!gate.isPending(),"Changed send-as identity sent");
  String[] editor={"Original"};gate.start(5000,()->editor[0].equals("Original"),()->sent[0]++,()->cancelled[0]++);editor[0]="New draft";f.now+=5000;f.scheduled.run();check(sent[0]==0&&editor[0].equals("New draft"),"Draft overwritten or changed text sent");
  gate.start(5000,()->true,()->sent[0]++,()->cancelled[0]++);f.now+=6001;f.scheduled.run();check(sent[0]==0&&!gate.isPending(),"Late resume sent");
  gate.start(5000,()->{throw new IllegalStateException();},()->sent[0]++,()->cancelled[0]++);f.now+=5000;f.scheduled.run();check(sent[0]==0&&!gate.isPending(),"Invalid gate exception sent");
  UndoSendDelay restarted=new UndoSendDelay(new Fake(),new Fake());check(!restarted.isPending(),"Restart resumed work");
  try{gate.start(100,()->true,()->{},()->{});throw new AssertionError("Invalid window");}catch(IllegalArgumentException expected){}
  System.out.println("Undo send: 3/5/10s, no early/duplicate send, cancel/replacement races, invalid owner/draft, late wakeup and fresh-process state passed");
 }
}''',encoding='utf-8')
 source=root/'TMessagesProj/src/main/java/org/telegram/messenger/UndoSendDelay.java'
 subprocess.run(['javac','-encoding','UTF-8','-d',temp,str(source),str(p)],check=True)
 subprocess.run(['java','-cp',temp,'UndoDelayTest'],check=True)
