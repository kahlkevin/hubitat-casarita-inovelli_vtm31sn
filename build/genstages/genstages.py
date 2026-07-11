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
import sys
import tomllib

import generate_stage_ninja


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
        description='Generate staged Ninja source files.',
    )
    parser.add_argument(
        '-q',
        '--quiet',
        action='store_true',
        help='Suppress normal command output.',
    )
    parser.add_argument(
        '--source-path',
        help='Path to project source root for dependency discovery.',
    )
    parser.add_argument('sources_toml', help='Path to the sources TOML file.')
    parser.add_argument('stage1_output_path', help='Path to write the stage 1 Ninja file.')
    parser.add_argument('stage2_output_path', help='Path to write the stage 2 Ninja file.')
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)

    if not args.quiet:
        print(f"{tool_name(sys.argv[0])}: generate staged Ninja source files")

    with open(args.sources_toml, 'rb') as input_file:
        sources = tomllib.load(input_file)

    generate_stage_ninja.generate(
        args.stage1_output_path,
        args.stage2_output_path,
        sources['libraries'],
        sources['drivers'],
        sources['assets'],
        args.source_path,
    )
    if not args.quiet:
        print(f"Generated {args.stage1_output_path}")
        print(f"Generated {args.stage2_output_path}")


if __name__ == '__main__':
    main()
