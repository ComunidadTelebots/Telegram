import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
import worker


class PublicationTests(unittest.TestCase):
    def test_exact_channel_mapping_no_fallback(self):
        config = json.loads(Path(__file__).with_name('channels.configured.json').read_text())
        self.assertEqual(worker.target(config, 'android', 'stable'), '-1004211289901')
        self.assertEqual(worker.target(config, 'react', 'rc'), '-1004326004401')
        with self.assertRaises(ValueError):
            worker.target({}, 'android', 'dev')

    def test_only_ready_tags(self):
        for tag in ('main', 'v1.0.0', 'ready/stable/../../secret', 'ready/unknown/1.0.0'):
            self.assertIsNone(worker.TAG.fullmatch(tag))
        self.assertIsNotNone(worker.TAG.fullmatch('ready/alfa/1.0.0-1'))

    def test_private_channels_never_public(self):
        for channel in ('dev', 'alfa', 'beta', 'rc'):
            with self.assertRaises(ValueError):
                worker.public_record('android', channel, '1.0.0', 'a', 'b', '@example', 1)

    def test_private_id_not_leaked(self):
        record = worker.public_record('android', 'stable', '1.0.0', 'a', 'b', '-1004211289901', 1)
        self.assertIsNone(record['download_url'])
        self.assertNotIn('4211289901', json.dumps(record))

    def database(self):
        db = sqlite3.connect(':memory:')
        db.execute('CREATE TABLE jobs(product,tag,sha,state,record)')
        return db

    def test_feed_keeps_newest_stable_not_last_inserted(self):
        db = self.database()
        for v in ('1.10.0', '1.9.0'):
            record = worker.public_record('android', 'stable', v, 'a', 'b', '@example', 1)
            db.execute('INSERT INTO jobs VALUES (?,?,?,?,?)', ('android', v, 'a', 'sent', json.dumps(record)))
        db.execute('INSERT INTO jobs VALUES (?,?,?,?,?)', ('react', 'rc', 'a', 'sent', 'null'))
        with tempfile.TemporaryDirectory() as directory:
            worker.write_public(db, Path(directory))
            self.assertEqual(json.loads((Path(directory) / 'android.json').read_text())['version'], '1.10.0')
            self.assertFalse((Path(directory) / 'react.json').exists())

    def test_uncertain_upload_is_not_automatically_retried(self):
        db = self.database()
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / 'app.apk'
            artifact.write_bytes(b'test artifact')
            artifact.with_name('application.metadata.json').write_text(json.dumps({'package': 'org.comunidadtelebots.stable', 'version_code': 10, 'version_name': '1.0.0'}))
            with patch.object(worker, 'verify_channel', return_value='@example'), patch.object(worker, 'build', return_value=(artifact, 'a'*40)), patch.object(worker, 'send_file', side_effect=TimeoutError):
                worker.process(db, Path(directory), 'android', 'ready/stable/1.0.0', 'b'*40, {'android': {'stable': '@example'}})
            self.assertEqual(db.execute('SELECT state FROM jobs').fetchone()[0], 'sending')

    def test_failed_build_never_uploads(self):
        db = self.database()
        with patch.object(worker, 'verify_channel', return_value='@example'), patch.object(worker, 'build', side_effect=RuntimeError), patch.object(worker, 'send_file') as send:
            worker.process(db, Path('.'), 'android', 'ready/rc/1.0.0', 'b'*40, {'android': {'rc': '@example'}})
            send.assert_not_called()
        self.assertEqual(db.execute('SELECT state FROM jobs').fetchone()[0], 'failed')

    def test_confirmed_upload_records_public_stable(self):
        db = self.database()
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / 'app.apk'
            artifact.write_bytes(b'test artifact')
            artifact.with_name('application.metadata.json').write_text(json.dumps({'package': 'org.comunidadtelebots.stable', 'version_code': 10, 'version_name': '1.0.0'}))
            with patch.object(worker, 'verify_channel', return_value='@example'), patch.object(worker, 'build', return_value=(artifact, 'a'*40)), patch.object(worker, 'send_file', return_value=123):
                worker.process(db, Path(directory), 'android', 'ready/stable/1.0.0', 'b'*40, {'android': {'stable': '@example'}})
        state, record = db.execute('SELECT state,record FROM jobs').fetchone()
        self.assertEqual(state, 'sent')
        self.assertEqual(json.loads(record)['download_url'], 'https://t.me/example/123')

    def test_missing_channel_never_builds(self):
        with patch.object(worker, 'build') as build:
            with self.assertRaises(ValueError):
                worker.process(self.database(), Path('.'), 'android', 'ready/dev/1.0.0', 'a'*40, {})
            build.assert_not_called()

    def test_private_update_only_after_confirmation_and_highest_code(self):
        db = self.database()
        worker.release_table(db)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for version, code, state in [('2.0.0', 20, 'sent'), ('1.0.0', 10, 'sent'), ('3.0.0', 30, 'sending')]:
                tag = 'ready/dev/' + version
                record = worker.android_record({'package': 'org.comunidadtelebots.dev', 'version_code': code, 'version_name': version}, 'dev', '-1004315521214', code, 'a'*40, 'b'*64)
                db.execute('INSERT INTO jobs VALUES (?,?,?,?,?)', ('android', tag, 'a', state, 'null'))
                db.execute('INSERT INTO android_releases VALUES (?,?)', (tag, json.dumps(record)))
            worker.write_android_releases(db, root / 'private')
            (root / 'public').mkdir()
            worker.write_public(db, root / 'public')
            record = json.loads((root / 'private/android/dev.json').read_text())
            self.assertEqual(record['version_code'], 20)
            self.assertEqual(record['url'], 'https://t.me/c/4315521214/20')
            self.assertEqual(list((root / 'public').iterdir()), [])

    def test_integrity_metadata_requires_actual_file_size(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / 'app.apk'
            artifact.write_bytes(b'test')
            valid = dict(package='org.comunidadtelebots.dev', version_code=20, version_name='2.0.0',
                         size_bytes=4, min_sdk=21, abis=['arm64-v8a'], signer_sha256=['a'*64], release_notes='Actual changes')
            for change in ({}, {'size_bytes': 5}, {'min_sdk': True}, {'abis': ['../etc']}, {'signer_sha256': []}):
                artifact.with_name('application.metadata.json').write_text(json.dumps(dict(valid, **change)))
                if change:
                    with self.assertRaises(ValueError):
                        worker.android_metadata(artifact, 'dev', '2.0.0')
                else:
                    self.assertEqual(worker.android_metadata(artifact, 'dev', '2.0.0')['size_bytes'], 4)

    def test_invalid_metadata_blocks_upload(self):
        db = self.database()
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / 'app.apk'
            artifact.write_bytes(b'test')
            artifact.with_name('application.metadata.json').write_text(json.dumps({'package': 'org.comunidadtelebots.beta', 'version_code': 10, 'version_name': '1.0.0'}))
            with patch.object(worker, 'verify_channel', return_value='@example'), patch.object(worker, 'build', return_value=(artifact, 'a'*40)), patch.object(worker, 'send_file') as send:
                worker.process(db, Path(directory), 'android', 'ready/dev/1.0.0', 'b'*40, {'android': {'dev': '@example'}})
                send.assert_not_called()
            self.assertEqual(db.execute('SELECT state FROM jobs').fetchone()[0], 'failed')


if __name__ == '__main__':
    unittest.main()
