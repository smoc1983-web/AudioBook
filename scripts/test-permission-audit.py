import runpy,unittest
s=runpy.run_path('scripts/audit-installable-apk.py');p='com.audiobookdownloader.preview'
class AuditTest(unittest.TestCase):
 def test_supported_media_release(self):s['audit_manifest']("package: name='"+p+"'\nuses-permission: name='android.permission.READ_MEDIA_AUDIO'\nuses-permission: name='android.permission.REQUEST_INSTALL_PACKAGES'",'A: android:debuggable(0x0101000f)=(type 0x12)0x0',p)
 def test_debug_build_is_rejected(self):
  with self.assertRaises(AssertionError):s['audit_manifest']("package: name='"+p+"'\napplication-debuggable",'',p)
 def test_sensitive_permission_is_rejected(self):
  for permission in s['FORBIDDEN_PERMISSIONS']:
   with self.assertRaises(AssertionError):s['audit_manifest']("package: name='"+p+"'\nuses-permission: name='"+permission+"'",'',p)
 def test_binding_service_is_rejected_even_without_uses_permission(self):
  with self.assertRaises(AssertionError):s['audit_manifest']("package: name='"+p+"'",'permission="android.permission.BIND_ACCESSIBILITY_SERVICE"',p)
 def test_unknown_permission_requires_review(self):
  with self.assertRaises(AssertionError):s['audit_manifest']("package: name='"+p+"'\nuses-permission: name='android.permission.CAMERA'",'',p)
 def test_retired_dex_types_are_rejected(self):
  s['audit_types'](['Lcom/audiobookdownloader/PlaybackService;'])
  for cls in s['RETIRED']:
   with self.assertRaises(AssertionError):s['audit_types']([cls+'$Service;'])
unittest.main()
