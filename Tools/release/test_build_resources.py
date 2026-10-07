import unittest
from unittest.mock import patch
from pathlib import Path
import tempfile
import worker
from build_resources import configuration, docker_arguments


class ResourceTests(unittest.TestCase):
    def test_shared_host_defaults_remain_bounded(self):
        config = configuration({})
        self.assertEqual(docker_arguments(config)[:3], ['--memory=3072m','--memory-swap=4096m','--cpus=1'])
        self.assertEqual(config['ANDROID_GRADLE_HEAP_MB'], 1800)
        self.assertEqual(config['ANDROID_GRADLE_WORKERS'], 1)

    def test_dedicated_profile_and_environment_forwarding(self):
        config = configuration({'ANDROID_BUILD_MEMORY_MB':'12288','ANDROID_BUILD_SWAP_MB':'12288',
                                'ANDROID_BUILD_CPUS':'4','ANDROID_GRADLE_HEAP_MB':'6144',
                                'ANDROID_GRADLE_METASPACE_MB':'1024','ANDROID_GRADLE_WORKERS':'2'})
        args = docker_arguments(config)
        self.assertIn('--memory=12288m', args)
        self.assertIn('ANDROID_GRADLE_HEAP_MB=6144', args)

    def test_swap_never_substitutes_for_ram(self):
        with self.assertRaisesRegex(ValueError, 'exceeds'):
            configuration({'ANDROID_BUILD_SWAP_MB':'16384','ANDROID_GRADLE_HEAP_MB':'6000'})
        with self.assertRaises(ValueError):
            configuration({'ANDROID_BUILD_SWAP_MB':'2048'})

    def test_actual_container_limit_overrides_declared_budget(self):
        with self.assertRaisesRegex(ValueError, 'exceeds'):
            configuration({}, actual_limit_mb=2048)

    def test_rejects_unbounded_or_injected_parameters(self):
        for key, value in [('ANDROID_BUILD_MEMORY_MB','-1'),('ANDROID_BUILD_CPUS','0'),
                           ('ANDROID_GRADLE_HEAP_MB','1800 -javaagent:foo'),
                           ('ANDROID_BUILD_MEMORY_MB','999999999'),('ANDROID_GRADLE_WORKERS','2')]:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                configuration({key:value})

    def test_invalid_config_does_not_clone_or_launch_container(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict('os.environ',{'ANDROID_GRADLE_HEAP_MB':'999999'}), patch.object(worker,'run') as run:
            with self.assertRaises(ValueError):
                worker.build(Path(directory),'android','ready/dev/1.0.0','a'*40,'dev')
            run.assert_not_called()
            self.assertEqual(list(Path(directory).iterdir()), [])


if __name__ == '__main__':
    unittest.main()
