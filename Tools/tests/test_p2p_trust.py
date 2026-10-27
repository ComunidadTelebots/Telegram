"""Real X.509 pin checks against production Java. Requires JDK and cryptography."""
import datetime
import pathlib
import subprocess
import tempfile
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID

root = pathlib.Path(__file__).resolve().parents[2]
java = r'''
import java.nio.file.*;
import java.io.*;
import java.security.cert.*;
import java.util.*;
import org.telegram.messenger.TelebotsPeerTrust;
class Check {
 static X509Certificate cert(String path) throws Exception {
  try(InputStream in=Files.newInputStream(Path.of(path))) {
   return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);
  }
 }
 static void reject(TelebotsPeerTrust t, X509Certificate[] chain) throws Exception {
  try { t.checkClientTrusted(chain,"EC"); throw new AssertionError("client accepted"); }
  catch(CertificateException expected) { }
  try { t.checkServerTrusted(chain,"EC"); throw new AssertionError("server accepted"); }
  catch(CertificateException expected) { }
 }
 public static void main(String[] args) throws Exception {
  X509Certificate good=cert(args[0]), foreign=cert(args[1]), expired=cert(args[2]);
  Set<String> pins=new HashSet<>(); pins.add(TelebotsPeerTrust.fingerprint(good));
  TelebotsPeerTrust trust=new TelebotsPeerTrust(pins);
  pins.clear(); // Trust snapshots cannot be changed by the caller.
  trust.checkClientTrusted(new X509Certificate[]{good},"EC");
  trust.checkServerTrusted(new X509Certificate[]{good},"EC");
  reject(trust,new X509Certificate[]{foreign});
  reject(trust,null); reject(trust,new X509Certificate[0]);
  reject(trust,new X509Certificate[]{good,foreign});
  reject(new TelebotsPeerTrust(Set.of(TelebotsPeerTrust.fingerprint(expired))),new X509Certificate[]{expired});
  reject(new TelebotsPeerTrust(Set.of()),new X509Certificate[]{good});
  if(trust.getAcceptedIssuers().length!=0) throw new AssertionError();
  System.out.println("PASS: mutual pin validation, foreign/expired/empty/multiple chains, revocation and immutable pins");
 }
}
'''
with tempfile.TemporaryDirectory() as directory:
    temp = pathlib.Path(directory)
    now = datetime.datetime.now(datetime.timezone.utc)
    for label in ('good', 'foreign', 'expired'):
        key = ec.generate_private_key(ec.SECP256R1())
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, label)])
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                .public_key(key.public_key()).serial_number(x509.random_serial_number())
                .not_valid_before(now-datetime.timedelta(days=2))
                .not_valid_after(now+datetime.timedelta(days=-1 if label=='expired' else 1))
                .sign(key, hashes.SHA256()))
        (temp/(label+'.pem')).write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    source = temp/'Check.java'
    source.write_text(java, encoding='utf-8')
    subprocess.run(['javac','-d',directory,str(source),str(root/'TMessagesProj/src/main/java/org/telegram/messenger/TelebotsPeerTrust.java')],check=True)
    subprocess.run(['java','-cp',directory,'Check',*[str(temp/(n+'.pem')) for n in ('good','foreign','expired')]],check=True)
