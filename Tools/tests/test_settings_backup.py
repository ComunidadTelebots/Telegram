"""Strict own-settings codec regression tests, running the real Java class."""
import pathlib, subprocess, tempfile, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parents[2]
test = r'''import org.telegram.messenger.CommunitySettingsBackup;
import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class BackupTest {
 static Set<String> allowed=new HashSet<>(Arrays.asList("android:current","ios"));
 static void ck(boolean v,String m){if(!v)throw new AssertionError(m);}
 static void bad(String s){try{CommunitySettingsBackup.decode(s.getBytes(StandardCharsets.UTF_8),allowed);throw new AssertionError("accepted malformed input");}catch(IllegalArgumentException expected){}}
 public static void main(String[]args)throws Exception{
  int count=0;
  for(String m:new String[]{"original","smooth","quick","fade","none"})for(String skin:new String[]{"android:current","ios","keep_current"})for(boolean amp:new boolean[]{false,true})for(int mode=-1;mode<=2;mode++){
   CommunitySettingsBackup.Values v=new CommunitySettingsBackup.Values(m,skin,amp,mode);
   byte[] encoded=CommunitySettingsBackup.encode(v);
   CommunitySettingsBackup.Values loaded=CommunitySettingsBackup.read(new ByteArrayInputStream(encoded),allowed);
   ck(loaded.motion.equals(m)&&loaded.skin.equals(skin)&&loaded.ampUnmetered==amp&&loaded.preload==mode,"roundtrip");count++;
  }
  String valid=new String(CommunitySettingsBackup.encode(new CommunitySettingsBackup.Values("smooth","ios",true,1)),StandardCharsets.UTF_8);
  for(String s:new String[]{valid+"token=secret\n",valid.replace("schema=1","schema=2"),valid.replace("motion=smooth","motion=secret"),valid.replace("skin=ios","skin=https://user:secret@host/"),valid.replace("skin=ios","skin=unknown"),valid.replace("amp_unmetered=true","amp_unmetered=1"),valid.replace("preload=1","preload=1.0"),valid.replace("preload=1","preload=3"),valid.replace("skin=ios","motion=fade"),valid.replace("skin=ios","token=secret"),valid.replace("schema=1","schema=01"),valid.replace("preload=1","preload=+1"),valid+"\n",valid.replace("format=telebots-settings","format=telegram")})bad(s);
  ck(CommunitySettingsBackup.decode(valid.replace("\n","\r\n").getBytes(StandardCharsets.UTF_8),allowed).ampUnmetered,"CRLF");
  try{CommunitySettingsBackup.decode(new byte[]{(byte)0xc3,(byte)0x28},allowed);throw new AssertionError("invalid UTF8 accepted");}catch(IllegalArgumentException expected){}
  try{CommunitySettingsBackup.read(new ByteArrayInputStream(new byte[1025]),allowed);throw new AssertionError("oversize accepted");}catch(IOException expected){}
  // Explicit allowlist: no serialized key can be supplied by external preferences.
  ck(valid.split("\n").length==6&&!valid.contains("account")&&!valid.contains("proxy")&&!valid.contains("token"),"closed export fields");
  System.out.println(count+" settings roundtrips; schema, duplicate, secrets, UTF8 and size rejection passed");
 }}'''
with tempfile.TemporaryDirectory(prefix='own-settings-tests-') as tmp:
    harness=pathlib.Path(tmp,'BackupTest.java'); harness.write_text(test,encoding='utf-8')
    source=root/'TMessagesProj/src/main/java/org/telegram/messenger/CommunitySettingsBackup.java'
    subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(source),str(harness)],check=True)
    subprocess.run(['java','-cp',tmp,'BackupTest'],check=True)
keys=[]
for lang in ('en','es'):
    keys.append({s.attrib['name'] for s in ET.parse(root/f'Tools/strings/settings-backup-{lang}.xml').getroot()})
assert keys[0]==keys[1] and len(keys[0])==19
print('Settings backup translations: 19 matching keys')
