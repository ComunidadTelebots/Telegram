"""Contract for outbound UDP socket lifetime in the experimental Telebots P2P implementation.

Source-only checks; this does not establish transport confidentiality or Android compatibility.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger"


class UdpSocketLifetimeContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.controller = (SRC / "TelebotsP2pIndependentMessaging.java").read_text(encoding="utf-8")
        cls.sender = (SRC / "TelebotsUdpSender.java").read_text(encoding="utf-8")

    def test_message_and_beacon_use_shared_sender(self):
        self.assertEqual(2, self.controller.count("TelebotsUdpSender.send("))

    def test_no_unmanaged_outbound_udp_sockets_in_controller(self):
        self.assertNotIn("DatagramSocket udpSocket = new DatagramSocket()", self.controller)
        self.assertNotIn("DatagramSocket socket = new DatagramSocket()", self.controller)
        self.assertIn("new DatagramSocket(P2P_UDP_BEACON_PORT)", self.controller)

    def test_sender_guarantees_socket_closure(self):
        self.assertIn("try (DatagramSocket socket = new DatagramSocket())", self.sender)
        self.assertIn("socket.setBroadcast(true);", self.sender)
        self.assertIn("socket.send(new DatagramPacket(payload, payload.length, destination, port));", self.sender)


if __name__ == "__main__":
    unittest.main()
