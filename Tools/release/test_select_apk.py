import json
from pathlib import Path
import tempfile
import unittest
from select_apk import select_apk


class ApkSelectionTests(unittest.TestCase):
    def fixture(self, root, location, variant='afatRelease', filename='app.apk', package='org.comunidadtelebots.dev', version_code=71129, version_name='12.10.6'):
        folder = Path(root) / location
        folder.mkdir(parents=True)
        (folder/'app.apk').write_bytes(b'fake-apk-for-selector-test')
        (folder/'output-metadata.json').write_text(json.dumps({'artifactType':{'type':'APK'},'variantName':variant,'applicationId':package,'elements':[{'type':'SINGLE','filters':[],'versionCode':version_code,'versionName':version_name,'outputFile':filename}]}))
        return folder/'app.apk'

    def test_agp_output_and_intermediate_locations(self):
        for location in ('outputs/apk/afat/release','intermediates/apk/afat/release'):
            with self.subTest(location=location), tempfile.TemporaryDirectory() as root:
                expected=self.fixture(root,location)
                self.assertEqual(select_apk(root,'dev'),expected.resolve())

    def test_debug_is_never_a_release_fallback(self):
        with tempfile.TemporaryDirectory() as root:
            self.fixture(root,'intermediates/apk/afat/debug',variant='afatDebug')
            with self.assertRaises(ValueError): select_apk(root,'dev')

    def test_rejects_traversal_and_wrong_channel(self):
        for changes in ({'filename':'../app.apk'},{'package':'org.comunidadtelebots.stable'}):
            with tempfile.TemporaryDirectory() as root:
                self.fixture(root,'outputs/apk/afat/release',**changes)
                with self.assertRaises(ValueError): select_apk(root,'dev')

    def test_identical_copies_prefer_outputs(self):
        with tempfile.TemporaryDirectory() as root:
            self.fixture(root,'intermediates/apk/afat/release')
            expected = self.fixture(root,'outputs/apk/afat/release')
            self.assertEqual(select_apk(root,'dev'),expected.resolve())

    def test_different_apk_bytes_abort_even_with_same_metadata(self):
        with tempfile.TemporaryDirectory() as root:
            self.fixture(root,'outputs/apk/afat/release')
            other = self.fixture(root,'intermediates/apk/afat/release')
            other.write_bytes(b'other-apk-bytes')
            with self.assertRaisesRegex(ValueError,'Ambiguous'): select_apk(root,'dev')

    def test_identical_bytes_with_different_version_metadata_abort(self):
        for change in ({'version_code':71130},{'version_name':'12.10.7'}):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as root:
                self.fixture(root,'outputs/apk/afat/release')
                self.fixture(root,'intermediates/apk/afat/release',**change)
                with self.assertRaisesRegex(ValueError,'Ambiguous'): select_apk(root,'dev')


if __name__=='__main__': unittest.main()
