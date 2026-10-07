"""Bounded Android build resources. Defaults preserve the shared VPS budget."""
import os
from pathlib import Path
import re
import sys

DEFAULTS = {
    'ANDROID_BUILD_MEMORY_MB': 3072,
    'ANDROID_BUILD_SWAP_MB': 4096,  # Docker total RAM + swap, not extra swap.
    'ANDROID_BUILD_CPUS': 1,
    'ANDROID_GRADLE_HEAP_MB': 1800,
    'ANDROID_GRADLE_METASPACE_MB': 512,
    'ANDROID_GRADLE_WORKERS': 1,
}


def configuration(environ=None, actual_limit_mb=None):
    env = os.environ if environ is None else environ
    result = {}
    for key, default in DEFAULTS.items():
        raw = str(env.get(key, default))
        if not re.fullmatch(r'[1-9][0-9]{0,5}', raw):
            raise ValueError(key + ' must be a positive integer')
        result[key] = int(raw)
    memory, swap = result['ANDROID_BUILD_MEMORY_MB'], result['ANDROID_BUILD_SWAP_MB']
    cpus, workers = result['ANDROID_BUILD_CPUS'], result['ANDROID_GRADLE_WORKERS']
    heap, meta = result['ANDROID_GRADLE_HEAP_MB'], result['ANDROID_GRADLE_METASPACE_MB']
    if not 1024 <= memory <= 65536 or not memory <= swap <= 131072:
        raise ValueError('Memory must be 1024..65536 MiB; total memory+swap must be at least memory and at most 131072 MiB')
    if not 1 <= workers <= cpus <= 8:
        raise ValueError('Workers must not exceed CPUs; CPUs must be 1..8')
    if heap < 256 or meta < 128:
        raise ValueError('Heap/metaspace must be at least 256/128 MiB')
    limit = min(memory, actual_limit_mb) if actual_limit_mb is not None else memory
    # Leave space for the VM, native compiler, runtime and container processes.
    # This is a lower bound, not proof that a build fits into this budget.
    if heap + meta + 512 * workers > limit:
        raise ValueError('Heap + metaspace + native reserve exceeds the container RAM limit; swap is not RAM')
    return result


def container_limit_mb():
    for path in ('/sys/fs/cgroup/memory.max', '/sys/fs/cgroup/memory/memory.limit_in_bytes'):
        try:
            value = Path(path).read_text().strip()
        except OSError:
            continue
        if value.isdigit() and 0 < int(value) < 1 << 60:
            return int(value) // (1024 * 1024)
    return None


def docker_arguments(config):
    result = [f"--memory={config['ANDROID_BUILD_MEMORY_MB']}m",
              f"--memory-swap={config['ANDROID_BUILD_SWAP_MB']}m",
              f"--cpus={config['ANDROID_BUILD_CPUS']}"]
    for key, value in config.items():
        result += ['-e', f'{key}={value}']
    return result


if __name__ == '__main__':
    try:
        config = configuration(actual_limit_mb=container_limit_mb())
    except ValueError as error:
        sys.exit(str(error))
    print('--max-workers=' + str(config['ANDROID_GRADLE_WORKERS']))
    print(f"-Dorg.gradle.jvmargs=-Xmx{config['ANDROID_GRADLE_HEAP_MB']}m -XX:MaxMetaspaceSize={config['ANDROID_GRADLE_METASPACE_MB']}m")
