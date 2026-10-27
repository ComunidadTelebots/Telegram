"""Check capability boundaries without depending on an Android runtime."""
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
source = '''
import org.telegram.messenger.CommunityCompatibility;
class Check {
 public static void main(String[] args) {
  for(int sdk=21;sdk<=37;sdk++) {
   if(CommunityCompatibility.supportsPeerIdentity(sdk)!=(sdk>=23)) throw new AssertionError("P2P "+sdk);
   if(CommunityCompatibility.supportsBundledTor(sdk)!=(sdk>=24)) throw new AssertionError("Tor "+sdk);
   if(CommunityCompatibility.supportsNotificationChannels(sdk)!=(sdk>=26)) throw new AssertionError("channels "+sdk);
   if(CommunityCompatibility.requiresNotificationPermission(sdk)!=(sdk>=33)) throw new AssertionError("permission "+sdk);
  }
  System.out.println("PASS: 68 platform capability boundary cases, API 21-37");
 }
}
'''
with tempfile.TemporaryDirectory() as temp:
    check = pathlib.Path(temp, 'Check.java')
    check.write_text(source, encoding='utf-8')
    subprocess.run(['javac','-d',temp,str(check),str(root/'TMessagesProj/src/main/java/org/telegram/messenger/CommunityCompatibility.java')],check=True)
    subprocess.run(['java','-cp',temp,'Check'],check=True)
