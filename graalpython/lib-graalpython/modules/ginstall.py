# Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import re
from pathlib import Path
import argparse
import json
import os
import shutil
import site
import subprocess
import tempfile
import importlib

import sys

WARNING = '\033[93m'
FAIL = '\033[91m'
ENDC = '\033[0m'
BOLD = '\033[1m'


def info(msg, *args, **kwargs):
    print(BOLD + msg.format(*args, **kwargs) + ENDC)


def error(msg, *args, **kwargs):
    print(FAIL + msg.format(*args, **kwargs) + ENDC)


def warn(msg, *args, **kwargs):
    print(WARNING + msg.format(*args, **kwargs) + ENDC)


def get_module_name(package_name):
    non_standard_packages = {
        'pyyaml': 'pyaml',
        'protobuf': 'google.protobuf',
        'python-dateutil': 'dateutil',
        'websocket-client': 'websocket',
        'attrs': 'attr',
    }
    module_name = non_standard_packages.get(package_name, package_name)
    return  module_name.replace('-', '_')


def pip_package(name=None, try_import=False):
    def decorator(func):
        def wrapper(*args, **kwargs):
            _name = name if name else func.__name__
            try:
                module_name = get_module_name(_name)
                importlib.import_module(module_name)
                importlib.invalidate_caches()
            except (ImportError, ModuleNotFoundError):
                info("Installing required dependency: {} ... ", _name)
                func(*args, **kwargs)
                if try_import:
                    import site
                    site.main()
                    importlib.invalidate_caches()
                    importlib.import_module(module_name)
                info("{} installed successfully", _name)
        return wrapper
    return decorator

def run_cmd(args, msg="", failOnError=True, cwd=None, env=None):
    cwd_log = "cd " + cwd if cwd else ""
    print("+", cwd_log, ' '.join(args))
    result = subprocess.run(args, cwd=cwd, env=env)
    if failOnError and result.returncode != 0:
        xit(msg, status=result.returncode)
    return result.returncode

def known_packages():
    @pip_package()
    def pytest(**kwargs):
        setuptools(**kwargs)
        wcwidth(**kwargs)
        pluggy(**kwargs)
        atomicwrites(**kwargs)
        more_itertools(**kwargs)
        attrs(**kwargs)
        packaging(**kwargs)
        py(**kwargs)
        install_from_pypi("pytest==5.1.0", **kwargs)

    @pip_package()
    def pytest_parallel(**kwargs):
        pytest(**kwargs)
        install_from_pypi("pytest-parallel==0.0.9", **kwargs)

    @pip_package()
    def py(**kwargs):
        install_from_pypi("py==1.8.0", **kwargs)

    @pip_package()
    def attrs(**kwargs):

        install_from_pypi("attrs==19.2.0", **kwargs)

    @pip_package()
    def pyparsing(**kwargs):
        install_from_pypi("pyparsing==2.4.2", **kwargs)

    @pip_package()
    def packaging(**kwargs):
        six(**kwargs)
        pyparsing(**kwargs)
        install_from_pypi("packaging==19.0", **kwargs)

    @pip_package()
    def more_itertools(**kwargs):
        install_from_pypi("more-itertools==7.0.0", **kwargs)

    @pip_package()
    def atomicwrites(**kwargs):
        install_from_pypi("atomicwrites==1.3.0", **kwargs)

    @pip_package()
    def pluggy(**kwargs):
        zipp(**kwargs)
        install_from_pypi("pluggy==0.13.1", **kwargs)

    @pip_package()
    def zipp(**kwargs):
        setuptools_scm(**kwargs)
        install_from_pypi("zipp==0.5.0", **kwargs)

    @pip_package()
    def wcwidth(**kwargs):
        six(**kwargs)
        install_from_pypi("wcwidth==0.1.7", **kwargs)

    @pip_package()
    def PyYAML(**kwargs):
        install_from_pypi("PyYAML==3.13", **kwargs)

    @pip_package()
    def six(**kwargs):
        install_from_pypi("six==1.12.0", **kwargs)

    @pip_package()
    def Cython(extra_opts=None, **kwargs):
        if extra_opts is None:
            extra_opts = []
        install_from_pypi("Cython==0.29.13", extra_opts=['--no-cython-compile'] + extra_opts, **kwargs)

    @pip_package()
    def setuptools(**kwargs):
        six(**kwargs)
        install_from_pypi("setuptools==41.0.1", **kwargs)

    @pip_package()
    def pkgconfig(**kwargs):
        install_from_pypi("pkgconfig==1.5.1", **kwargs)

    @pip_package()
    def wheel(**kwargs):
        install_from_pypi("wheel==0.33.4", **kwargs)

    @pip_package()
    def protobuf(**kwargs):
        install_from_pypi("protobuf==3.8.0", **kwargs)

    @pip_package()
    def Keras_preprocessing(**kwargs):
        install_from_pypi("Keras-Preprocessing==1.0.5", **kwargs)

    @pip_package()
    def gast(**kwargs):
        install_from_pypi("gast==0.2.2", **kwargs)

    @pip_package()
    def astor(**kwargs):
        install_from_pypi("astor==0.8.0", **kwargs)

    @pip_package()
    def absl_py(**kwargs):
        install_from_pypi("absl-py==0.7.1", **kwargs)

    @pip_package()
    def mock(**kwargs):
        install_from_pypi("mock==3.0.5", **kwargs)

    @pip_package()
    def Markdown(**kwargs):
        install_from_pypi("Markdown==3.1.1", **kwargs)

    @pip_package()
    def Werkzeug(**kwargs):
        install_from_pypi("Werkzeug==0.15.4", **kwargs)

    @pip_package()
    def h5py(**kwargs):
        numpy(**kwargs)
        Cython(**kwargs)
        install_from_pypi("h5py==2.10.0", **kwargs)

    @pip_package()
    def sortedcontainers(**kwargs):
        install_from_pypi("sortedcontainers==2.1.0", **kwargs)

    @pip_package()
    def hypothesis(**kwargs):
        setuptools(**kwargs)
        attrs(**kwargs)
        sortedcontainers(**kwargs)
        install_from_pypi("hypothesis==5.41.1", **kwargs)

    # Does not yet work
    # def h5py(**kwargs):
    #     try:
    #         import pkgconfig
    #     except ImportError:
    #         print("Installing required dependency: pkgconfig")
    #         pkgconfig(**kwargs)
    #     install_from_pypi("h5py==2.9.0", **kwargs)
    #     try:
    #         import six
    #     except ImportError:
    #         print("Installing required dependency: six")
    #         pkgconfig(**kwargs)
    #     install_from_pypi("six==1.12.0", **kwargs)
    #
    # def keras_applications(**kwargs):
    #     try:
    #         import h5py
    #     except ImportError:
    #         print("Installing required dependency: h5py")
    #         h5py(**kwargs)
    #     install_from_pypi("Keras-Applications==1.0.6", **kwargs)

    @pip_package()
    def setuptools_scm(**kwargs):
        setuptools(**kwargs)
        install_from_pypi("setuptools_scm==1.15.0", **kwargs)

    @pip_package()
    def numpy(**kwargs):
        setuptools(**kwargs)
        # honor following selected env variables: BLAS, LAPACK, ATLAS
        numpy_build_env = {}
        for key in ("BLAS", "LAPACK", "ATLAS"):
            if key in os.environ:
                numpy_build_env[key] = os.environ[key]
        install_from_pypi("numpy==1.16.4", env=numpy_build_env, **kwargs)

    @pip_package()
    def dateutil(**kwargs):
        setuptools_scm(**kwargs)
        install_from_pypi("python-dateutil==2.7.5", **kwargs)

    @pip_package()
    def certifi(**kwargs):
        install_from_pypi("certifi==2020.11.8", **kwargs)

    @pip_package()
    def idna(**kwargs):
        install_from_pypi("idna==2.8", **kwargs)

    @pip_package()
    def chardet(**kwargs):
        install_from_pypi("chardet==3.0.4", **kwargs)

    @pip_package()
    def urllib3(**kwargs):
        install_from_pypi("urllib3==1.25.6", **kwargs)

    @pip_package()
    def requests(**kwargs):
        idna(**kwargs)
        certifi(**kwargs)
        chardet(**kwargs)
        urllib3(**kwargs)
        install_from_pypi("requests==2.22", **kwargs)

    @pip_package()
    def lightfm(**kwargs):
        # pandas(**kwargs)
        requests(**kwargs)
        install_from_pypi("lightfm==1.15", **kwargs)

    @pip_package()
    def pytz(**kwargs):
        install_from_pypi("pytz==2018.7", **kwargs)

    @pip_package()
    def pandas(**kwargs):
        pytz(**kwargs)
        six(**kwargs)
        dateutil(**kwargs)
        numpy(**kwargs)

        # download pandas-0.25.0
        install_from_pypi("pandas==0.25.0", **kwargs)

    @pip_package()
    def scipy(**kwargs):
        # honor following selected env variables: BLAS, LAPACK, ATLAS
        scipy_build_env = {}
        for key in ("BLAS", "LAPACK", "ATLAS"):
            if key in os.environ:
                scipy_build_env[key] = os.environ[key]
        
        if sys.implementation.name == "graalpython":
            if not os.environ.get("VIRTUAL_ENV", None):
                xit("SciPy can only be installed within a venv.")
            from distutils.sysconfig import get_config_var
            scipy_build_env["LDFLAGS"] = get_config_var("LDFLAGS")

        # install dependencies
        numpy(**kwargs)

        install_from_pypi("scipy==1.3.1", env=scipy_build_env, **kwargs)

    @pip_package()
    def cycler(**kwargs):
        six(**kwargs)
        install_from_pypi("cycler==0.10.0", **kwargs)

    @pip_package()
    def cppy(**kwargs):
        install_from_pypi("cppy==1.1.0", **kwargs)

    @pip_package()
    def cassowary(**kwargs):
        install_from_pypi("cassowary==0.5.2", **kwargs)

    @pip_package()
    def Pillow(**kwargs):
        setuptools(**kwargs)
        build_env = {"MAX_CONCURRENCY": "0"}
        install_from_pypi("Pillow==6.2.0", build_cmd=["build_ext", "--disable-jpeg"], env=build_env, **kwargs)
        
    @pip_package()
    def matplotlib(**kwargs):
        setuptools(**kwargs)
        certifi(**kwargs)
        cycler(**kwargs)
        cassowary(**kwargs)
        pyparsing(**kwargs)
        dateutil(**kwargs)
        numpy(**kwargs)
        Pillow(**kwargs)

        def download_freetype(extracted_dir):
            target_dir = os.path.join(extracted_dir, "build")
            os.makedirs(target_dir, exist_ok=True)
            package_pattern = os.environ.get("GINSTALL_PACKAGE_PATTERN", "https://sourceforge.net/projects/freetype/files/freetype2/2.6.1/%s.tar.gz")
            _download_with_curl_and_extract(target_dir, package_pattern % "freetype-2.6.1")

        install_from_pypi("matplotlib==3.3.2", pre_install_hook=download_freetype, **kwargs)

    return locals()


KNOWN_PACKAGES = known_packages()


def xit(msg, status=-1):
    error(msg)
    exit(-1)


def _download_with_curl_and_extract(dest_dir, url):
    name = url[url.rfind("/")+1:]

    downloaded_path = os.path.join(dest_dir, name)

    # first try direct connection
    if run_cmd(["curl", "-L", "-o", downloaded_path, url], failOnError=False) != 0:
        # honor env var 'HTTP_PROXY', 'HTTPS_PROXY', and 'NO_PROXY'
        env = os.environ
        curl_opts = []
        using_proxy = False
        if url.startswith("http://") and "HTTP_PROXY" in env:
            curl_opts += ["--proxy", env["HTTP_PROXY"]]
            using_proxy = True
        elif url.startswith("https://") and "HTTPS_PROXY" in env:
            curl_opts += ["--proxy", env["HTTPS_PROXY"]]
            using_proxy = True
        if using_proxy and "NO_PROXY" in env:
            curl_opts += ["--noproxy", env["NO_PROXY"]]
        run_cmd(["curl", "-L"] + curl_opts + ["-o", downloaded_path, url], msg="Download error")

    if name.endswith(".tar.gz"):
        run_cmd(["tar", "xzf", downloaded_path, "-C", dest_dir], msg="Error extracting tar.gz")
        bare_name = name[:-len(".tar.gz")]
    elif name.endswith(".tar.bz2"):
        run_cmd(["tar", "xjf", downloaded_path, "-C", dest_dir], msg="Error extracting tar.bz2")
        bare_name = name[:-len(".tar.bz2")]
    elif name.endswith(".zip"):
        run_cmd(["unzip", "-u", downloaded_path, "-d", dest_dir], msg="Error extracting zip")
        bare_name = name[:-len(".zip")]
    else:
        xit("Unknown file type: %s" % name)

    return bare_name


def _install_from_url(url, package, extra_opts=[], add_cflags="", ignore_errors=False, env={}, version=None, pre_install_hook=None, build_cmd=[]):
    tempdir = tempfile.mkdtemp()

    os_env = os.environ

    # honor env var 'CFLAGS' and the explicitly passed env
    setup_env = os_env.copy()
    setup_env.update(env)
    cflags = os_env.get("CFLAGS", "") + ((" " + add_cflags) if add_cflags else "")
    setup_env['CFLAGS'] = cflags if cflags else ""

    bare_name = _download_with_curl_and_extract(tempdir, url)

    file_realpath = os.path.dirname(os.path.realpath(__file__))
    patches_dir = os.path.join(Path(file_realpath).parent, 'patches', package)
    # empty match group to have the same groups range as in pip_hook
    # unlike with pip, the version number may not be available at all
    versions = re.search("()(\\d+)?(.\\d+)?(.\\d+)?", "" if version is None else version)

    patch_file_path = first_existing(package, versions, os.path.join(patches_dir, "sdist"), ".patch")
    if patch_file_path:
        run_cmd(["patch", "-d", os.path.join(tempdir, bare_name, ""), "-p1", "-i", patch_file_path])

    whl_patches_dir = os.path.join(patches_dir, "whl")
    patch_file_path = first_existing(package, versions, whl_patches_dir, ".patch")
    subdir = read_first_existing(package, versions, whl_patches_dir, ".dir")
    subdir = "" if subdir is None else subdir
    if patch_file_path:
        os.path.join(tempdir, bare_name, subdir)
        run_cmd(["patch", "-d", os.path.join(tempdir, bare_name, subdir), "-p1", "-i", patch_file_path])

    if pre_install_hook:
        pre_install_hook(os.path.join(tempdir, bare_name))

    if "--user" not in extra_opts and "--prefix" not in extra_opts and site.ENABLE_USER_SITE:
        user_arg = ["--user"]
    else:
        user_arg = []
    status = run_cmd([sys.executable, "setup.py"] + build_cmd + ["install"] + user_arg + extra_opts, env=setup_env,
                     cwd=os.path.join(tempdir, bare_name))
    if status != 0 and not ignore_errors:
        xit("An error occurred trying to run `setup.py install %s %s'" % (user_arg, " ".join(extra_opts)))

# NOTE: Following 3 functions are duplicated in pip_hook.py:
# creates a search list of a versioned file:
# {name}-X.Y.Z.{suffix}, {name}-X.Y.{suffix}, {name}-X.{suffix}, {name}.{suffix}
# 'versions' is a result of re.search
def list_versioned(pkg_name, versions, dir, suffix):
    acc = ""
    res = []
    for i in range(2,5):
        v = versions.group(i)
        if v is not None:
            acc = acc + v
            res.append(acc)
    res.reverse()
    res = [os.path.join(dir, pkg_name + "-" + ver + suffix) for ver in res]
    res.append(os.path.join(dir, pkg_name + suffix))
    return res

def first_existing(pkg_name, versions, dir, suffix):
    for filename in list_versioned(pkg_name, versions, dir, suffix):
        if os.path.exists(filename):
            return filename

def read_first_existing(pkg_name, versions, dir, suffix):
    filename = first_existing(pkg_name, versions, dir, suffix)
    if filename:
        with open(filename, "r") as f:
            return f.read()

# end of code duplicated in pip_hook.py

def install_from_pypi(package, extra_opts=[], add_cflags="", ignore_errors=True, env=None, pre_install_hook=None, build_cmd=[]):
    package_pattern = os.environ.get("GINSTALL_PACKAGE_PATTERN", "https://pypi.org/pypi/%s/json")
    package_version_pattern = os.environ.get("GINSTALL_PACKAGE_VERSION_PATTERN", "https://pypi.org/pypi/%s/%s/json")

    version = None
    if "==" in package:
        package, _, version = package.rpartition("==")
        url = package_version_pattern % (package, version)
    else:
        url = package_pattern % package

    if any(url.endswith(ending) for ending in [".zip", ".tar.bz2", ".tar.gz"]):
        # this is already the url to the actual package
        pass
    else:
        r = subprocess.check_output("curl -L %s" % url, shell=True).decode("utf8")
        url = None
        try:
            urls = json.loads(r)["urls"]
        except:
            pass
        else:
            for url_info in urls:
                if url_info["python_version"] == "source":
                    url = url_info["url"]
                    break

    # make copy of env
    env = env.copy() if env is not None else os.environ.copy()
    from distutils.sysconfig import get_config_var

    def set_if_exists(env_var, conf_var):
        conf_value = get_config_var(conf_var)
        if conf_value:
            env.setdefault(env_var, conf_value)

    set_if_exists("CC", "CC")
    set_if_exists("CXX", "CXX")
    set_if_exists("AR", "AR")
    set_if_exists("RANLIB", "RANLIB")
    set_if_exists("CFLAGS", "CFLAGS")
    set_if_exists("LDFLAGS", "CCSHARED")

    if url:
        _install_from_url(url, package=package, extra_opts=extra_opts, add_cflags=add_cflags,
                          ignore_errors=ignore_errors, env=env, version=version, pre_install_hook=pre_install_hook,
                          build_cmd=build_cmd)
    else:
        xit("Package not found: '%s'" % package)

def get_site_packages_path():
    if site.ENABLE_USER_SITE:
        return site.getusersitepackages()
    else:
        for s in site.getsitepackages():
            if s.endswith("site-packages"):
                return s
    return None

def main(argv):
    parser = argparse.ArgumentParser(description="The simple Python package installer for GraalVM")

    subparsers = parser.add_subparsers(title="Commands", dest="command", metavar="Use COMMAND --help for further help.")

    subparsers.add_parser(
        "list",
        help="list locally installed packages"
    )

    install_parser = subparsers.add_parser(
        "install",
        help="install a known package",
        description="Install a known package. Known packages are:\n" + "\n".join(sorted(KNOWN_PACKAGES.keys())),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    install_parser.add_argument(
        "package",
        help="comma-separated list"
    )
    install_parser.add_argument(
        "--prefix",
        help="user-site path prefix"
    )
    install_parser.add_argument(
        "--user",
        action='store_true',
        help="install into user site",
    )

    subparsers.add_parser(
        "uninstall",
        help="remove installation folder of a local package",
    ).add_argument(
        "package",
        help="comma-separated list"
    )

    subparsers.add_parser(
        "pypi",
        help="attempt to install a package from PyPI (untested, likely won't work, and it won't install dependencies for you)",
        description="Attempt to install a package from PyPI"
    ).add_argument(
        "package",
        help="comma-separated list, can use `==` at the end of a package name to specify an exact version"
    )

    args = parser.parse_args(argv)

    if args.command == "list":
        user_site = get_site_packages_path()
        info("Installed packages:")
        for p in sys.path:
            if p.startswith(user_site):
                info(p[len(user_site) + 1:])
    elif args.command == "uninstall":
        warn("WARNING: I will only delete the package folder, proper uninstallation is not supported at this time.")
        user_site = get_site_packages_path()
        for pkg in args.package.split(","):
            deleted = False
            for p in sys.path:
                if p.startswith(user_site):
                    # +1 due to the path separator
                    pkg_name = p[len(user_site)+1:]
                    if pkg_name.startswith(pkg):
                        if os.path.isdir(p):
                            shutil.rmtree(p)
                        else:
                            os.unlink(p)
                        deleted = True
                        break
            if deleted:
                info("Deleted {}", p)
            else:
                xit("Unknown package: '%s'" % pkg)
    elif args.command == "install":
        for pkg in args.package.split(","):
            if pkg not in KNOWN_PACKAGES:
                xit("Unknown package: '%s'" % pkg)
            else:
                extra_opts = []
                if args.prefix:
                    extra_opts += ["--prefix", args.prefix]
                if args.user:
                    extra_opts += ["--user"]
                KNOWN_PACKAGES[pkg](extra_opts=extra_opts)
    elif args.command == "pypi":
        for pkg in args.package.split(","):
            install_from_pypi(pkg, ignore_errors=False)


if __name__ == "__main__":
    main(sys.argv[1:])
