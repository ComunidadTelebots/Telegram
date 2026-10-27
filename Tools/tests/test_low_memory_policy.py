"""Compile the real low-memory detection against platform stubs."""
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[2]
s=(root/'TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java').read_text(encoding='utf-8')
method=s.split('    private static Boolean lowRamDevice;',1)[1].split('    public static String performanceClassName',1)[0]
java="""
class Check {
 static Boolean lowRamDevice;
 static class ActivityManager {
  boolean low; long total;
  static class MemoryInfo { long totalMem; }
  boolean isLowRamDevice(){return low;}
  void getMemoryInfo(MemoryInfo info){info.totalMem=total;}
 }
 static class Context {
  static String ACTIVITY_SERVICE="activity";
  ActivityManager manager=new ActivityManager();
  Object getSystemService(String service){return manager;}
 }
 static class ApplicationLoader {static Context applicationContext=new Context();}
"""+method+"""
 public static void main(String[] args) {
  int count=0;
  for(boolean flag:new boolean[]{false,true}) for(long memory:new long[]{0,1024L<<20,2048L<<20,(2048L<<20)+1,4096L<<20}) {
   lowRamDevice=null;
   ApplicationLoader.applicationContext.manager.low=flag;
   ApplicationLoader.applicationContext.manager.total=memory;
   if(isLowRamDevice()!=(flag || memory>0 && memory<=(2048L<<20)))throw new AssertionError();
   count++;
  }
  lowRamDevice=null; ApplicationLoader.applicationContext=null;
  if(isLowRamDevice() || lowRamDevice!=null)throw new AssertionError();
  System.out.println("Low RAM: "+count+" boundary cases and null-context retry passed");
 }
}
"""
with tempfile.TemporaryDirectory() as temp:
 p=pathlib.Path(temp,'Check.java');p.write_text(java,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',temp,str(p)],check=True)
 subprocess.run(['java','-cp',temp,'Check'],check=True)
