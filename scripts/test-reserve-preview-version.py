import runpy,unittest
reserve=runpy.run_path('scripts/reserve-preview-version.py')['reserve']
class VersionTest(unittest.TestCase):
 def test_run_offset_remains_compatible(self):self.assertEqual(89,reserve('88',''))
 def test_manual_release_forces_next_version(self):self.assertEqual(91,reserve('89','abc\trefs/tags/preview-v100090\nabc\trefs/tags/preview-v100088'))
 def test_higher_ci_run_still_wins(self):self.assertEqual(101,reserve('100','abc\trefs/tags/preview-v100090'))
 def test_wrong_channels_and_malformed_tags_are_ignored(self):self.assertEqual(3,reserve('2','abc\trefs/tags/stable-v999999\nabc\trefs/tags/preview-v0100090\nabc\trefs/tags/preview-v100999-malicious'))
 def test_overflow_and_bad_run_fail(self):
  for run,refs in [('0',''),('-1',''),('abc',''),('1','abc\trefs/tags/preview-v2147483647')]:
   with self.assertRaises(ValueError):reserve(run,refs)
unittest.main()
