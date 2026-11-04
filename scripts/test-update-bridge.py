"""Contract checks for the staged, unpublished legacy/GitHub bridge. No keys or network."""
import copy
import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('bridge',Path(__file__).resolve().parents[1]/'tools/prepare-update-bridge.py')
bridge=importlib.util.module_from_spec(spec);spec.loader.exec_module(bridge)

class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);(self.root/'parts').mkdir();self.apk=self.root/'Audiobook-Preview.apk'
        self.apk.write_bytes(b'abc');(self.root/'parts/test.bin').write_bytes(b'abc')
        sha=hashlib.sha256(b'abc').hexdigest()
        self.github={'versionCode':100093,'versionName':'4.2.93','packageName':'com.audiobookdownloader.preview','size':3,'sha256':sha,'certificateSha256':bridge.packager.CERTIFICATES['preview'],'apk':'Audiobook-Preview.apk','changes':[]}
        self.legacy=copy.deepcopy(self.github);self.legacy['parts']=[{'url':'parts/test.bin','size':3,'sha256':sha}]
    def check(self):return bridge.verify_pair(self.legacy,self.github,self.root,self.apk,100092)
    def test_same_bytes_in_both_formats(self):self.assertTrue(self.check()['sameApkInBothChannels'])
    def test_same_or_lower_version_cannot_update_4292(self):
        for code in [100091,100092]:
            self.github['versionCode']=self.legacy['versionCode']=code
            with self.assertRaises(ValueError):self.check()
    def test_package_and_signer_cannot_change(self):
        for key,value in [('packageName','com.audiobookdownloader'),('certificateSha256','ab'*32)]:
            old=self.github[key];self.github[key]=self.legacy[key]=value
            with self.assertRaises(ValueError):self.check()
            self.github[key]=self.legacy[key]=old
    def test_changed_github_apk_rejected(self):
        self.apk.write_bytes(b'abd')
        with self.assertRaises(ValueError):self.check()
    def test_truncated_part_rejected(self):
        (self.root/'parts/test.bin').write_bytes(b'ab')
        with self.assertRaises(ValueError):self.check()
    def test_same_size_corrupted_part_rejected(self):
        (self.root/'parts/test.bin').write_bytes(b'abd')
        with self.assertRaises(ValueError):self.check()
    def test_paths_and_duplicate_parts_rejected(self):
        self.legacy['parts'][0]['url']='../Audiobook-Preview.apk'
        with self.assertRaises(ValueError):self.check()
        self.legacy['parts'][0]['url']='parts/test.bin';self.legacy['parts']*=2
        with self.assertRaises(ValueError):self.check()
    def test_mismatched_metadata_rejected(self):
        self.legacy['sha256']='ab'*32
        with self.assertRaises(ValueError):self.check()
    def test_parts_not_allowed_in_github_manifest(self):
        self.github['parts']=self.legacy['parts']
        with self.assertRaises(ValueError):self.check()
    def test_version_name_is_not_independent_from_code(self):
        self.github['versionName']=self.legacy['versionName']='4.2.94'
        with self.assertRaises(ValueError):self.check()

if __name__=='__main__':unittest.main()
