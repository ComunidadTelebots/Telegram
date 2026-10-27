"""Exercise actual bounded framing, including a real local TCP connection."""
import pathlib, subprocess, tempfile

root = pathlib.Path(__file__).resolve().parents[2]
test = r'''
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.telegram.messenger.TelebotsP2pFrame;
class Check {
 static void rejected(byte[] bytes) throws Exception {
  try { TelebotsP2pFrame.read(new ByteArrayInputStream(bytes)); throw new AssertionError(); }
  catch(IOException expected) { }
 }
 public static void main(String[] args) throws Exception {
  rejected("partial".getBytes(StandardCharsets.UTF_8));
  rejected(new byte[8193]);
  byte[] max=new byte[8193]; java.util.Arrays.fill(max,(byte)'x'); max[8192]='\n';
  if(TelebotsP2pFrame.read(new ByteArrayInputStream(max)).length()!=8192) throw new AssertionError();
  try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
      Socket sender=new Socket(InetAddress.getLoopbackAddress(),listener.getLocalPort());
      Socket receiver=listener.accept()) {
   receiver.setSoTimeout(1000);
   sender.getOutputStream().write("{\"text\":\"prueba local\"}\n".getBytes(StandardCharsets.UTF_8));
   if(!TelebotsP2pFrame.read(receiver.getInputStream()).contains("prueba local")) throw new AssertionError();
   try { TelebotsP2pFrame.read(receiver.getInputStream()); throw new AssertionError(); }
   catch(SocketTimeoutException expected) { }
  }
  System.out.println("P2P framing: TCP delivery, size limit, incomplete frame and idle timeout passed");
 }
}
'''
with tempfile.TemporaryDirectory() as temp:
    p = pathlib.Path(temp, 'Check.java'); p.write_text(test, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(p), str(root/'TMessagesProj/src/main/java/org/telegram/messenger/TelebotsP2pFrame.java')], check=True)
    subprocess.run(['java', '-cp', temp, 'Check'], check=True)
