#!/usr/bin/env python3
###
##  SPDX-FileCopyrightText: 2026 Kevin Kahl
##  SPDX-License-Identifier: Apache-2.0
##
##  Copyright 2026 Kevin Kahl
##
##  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
##  in compliance with the License. You may obtain a copy of the License at:
##
##      http://www.apache.org/licenses/LICENSE-2.0
##
##  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
##  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
##  for the specific language governing permissions and limitations under the License.
##

import argparse
import os
import subprocess
import sys
import tomllib


class UsageOnErrorArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        self.print_help(sys.stderr)
        self.exit(2, f'\n{self.prog}: error: {message}\n')


def tool_name(argv0):
    name = os.path.basename(argv0)
    return name[:-3] if name.endswith('.py') else name


def parse_args(argv):
    parser = UsageOnErrorArgumentParser(
        prog=tool_name(sys.argv[0]),
        description='Generate a git version Ninja file.',
    )
    parser.add_argument(
        '-q',
        '--quiet',
        action='store_true',
        help='Suppress normal command output.',
    )
    parser.add_argument(
        '--release',
        action='store_true',
        help='Generate release version.'
    )
    parser.add_argument('sources_toml', help='Path to the sources TOML file.')
    parser.add_argument('version_output_path', help='Path to write the git version Ninja file.')
    return parser.parse_args(argv)


def write_if_changed(output_path, content):
    output_dir = os.path.dirname(output_path)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    try:
        with open(output_path, 'r') as output_file:
            if output_file.read() == content:
                return False
    except FileNotFoundError:
        pass

    with open(output_path, 'w') as output_file:
        output_file.write(content)
    return True


def git_version():
    result = subprocess.run(
        ['git', 'describe', '--always', '--dirty'],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.rstrip('\r\n')


def main(argv=None):
    pre: str = ""
    args = parse_args(argv)

    if not args.quiet:
        print(f"{tool_name(sys.argv[0])}: generate git version Ninja file")

    with open(args.sources_toml, 'rb') as input_file:
        sources = tomllib.load(input_file)

    version_suffix = git_version()

    if not args.release:
        pre = '-pre'
    elif version_suffix.endswith('-dirty'):
        print(f"{tool_name(sys.argv[0])}: error: release version includes dirty git state", file=sys.stderr)
        sys.exit(1)

    content = f'gpp_version_macro = -DVERSION="{sources.get("version", "unknown")}{pre}+{version_suffix}"\n'
    write_if_changed(args.version_output_path, content)

    if not args.quiet:
        print(f"Generated {args.version_output_path} <- {content.strip()}")


if __name__ == '__main__':
    main()
