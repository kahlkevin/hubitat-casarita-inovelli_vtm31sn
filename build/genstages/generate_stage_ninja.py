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

import os
import re
from typing import Dict, List, NamedTuple

import ninja_syntax

class SourceDeps(NamedTuple):
    include_files: List[str]
    inclib_names: List[str]


def find_deps(source_path: str, source_dir: str, source_name: str) -> SourceDeps:
    groovy_path = os.path.join(source_path, source_dir, f'{source_name}.groovy')
    include_file_pattern = re.compile(r'^//\s*#include\s+"([^"]+)"')
    inclib_pattern = re.compile(r'@(?:FIRST_)?INCLIB\(([^)]+)\)')

    with open(groovy_path, 'r') as groovy_file:
        include_files = []
        inclib_names = []
        for line in groovy_file:
            include_file_match = include_file_pattern.search(line)
            if include_file_match:
                include_files.append(include_file_match.group(1))
            inclib_names.extend(
                match.group(1)
                for match in inclib_pattern.finditer(line)
            )
        return SourceDeps(include_files, inclib_names)


def include_dep_path(include_file: str) -> str:
    include_dir, include_name = include_file.split('/', 1) if '/' in include_file else ('', include_file)
    include_var = f'{include_dir}incdir' if include_dir else 'incdir'
    return f'${include_var}/{include_name}'


def build_groovy(n: ninja_syntax.Writer, output: str, input_path: str, implicit: List[str]) -> None:
    if not implicit:
        n.build(output, 'gpp_groovy', input_path)
        return

    n.build(
        output,
        'gpp_groovy',
        input_path,
        implicit=implicit,
        inputs_per_line=True,
    )


def generate(
    stage1_path: str,
    stage2_path: str,
    libraries: List[str],
    drivers: List[str],
    assets: List[str],
    source_path: str|None = None,
) -> None:

    # Pre-compute final paths
    final_libraries = [f'${{lib_prefix}}{library}.groovy' for library in libraries]
    final_drivers = [f'${{driver_prefix}}{driver}.groovy' for driver in drivers]
    final_assets = [f'{asset}.txt' for asset in assets]
    library_deps: Dict[str, SourceDeps] = {
        library: find_deps(source_path, 'libraries', library)
        for library in libraries
    } if source_path else {}
    driver_deps: Dict[str, SourceDeps] = {
        driver: find_deps(source_path, 'drivers', driver)
        for driver in drivers
    } if source_path else {}

    # Ensure output paths
    for output_path in (stage1_path, stage2_path):
        output_dir = os.path.dirname(output_path)
        if output_dir:
            os.makedirs(output_dir, exist_ok=True)

    # Generate stage1 .ninja using ninja_syntax module
    with open(stage1_path, 'w') as output:
        n = ninja_syntax.Writer(output, 256)

        n.comment('SPDX-FileCopyrightText: 2026 Kevin Kahl')
        n.comment('SPDX-License-Identifier: Apache-2.0')

        n.comment(os.path.basename(stage1_path))

        n.newline(); n.comment('This file is generated and should not be edited directly.')
        n.newline(); n.comment('Generated/preprocessed outputs.')

        # Build statements
        for library, final_library in zip(libraries, final_libraries):
            include_deps = [
                include_dep_path(include_file)
                for include_file in library_deps.get(library, SourceDeps([], [])).include_files
            ]
            build_groovy(
                n,
                f'$gendir/{final_library}',
                f'$libdir/{library}.groovy',
                include_deps,
            )
        for driver, final_driver in zip(drivers, final_drivers):
            include_deps = [
                include_dep_path(include_file)
                for include_file in driver_deps.get(driver, SourceDeps([], [])).include_files
            ]
            build_groovy(
                n,
                f'$gendir/{final_driver}',
                f'$drvdir/{driver}.groovy',
                include_deps,
            )
        for asset, final_asset in zip(assets, final_assets):
            n.build(f'$stagedir/{final_asset}', 'gpp_template', f'$assetdir/{asset}-$flavor.txt.template')

        # 'genlibs' phony target rule
        n.newline(); n.build('genlibs', 'phony', [f'$gendir/{lib}' for lib in final_libraries], inputs_per_line=True)

        # 'stagelibs' phony target rule
        n.newline(); n.build('stagelibs', 'phony', [f'$stagedir/{lib}' for lib in final_libraries], inputs_per_line=True)

        # 'stagedrivers' phony target rule
        n.newline(); n.build('stagedrivers', 'phony', [f'$stagedir/{drv}' for drv in final_drivers], inputs_per_line=True)

        # 'stageassets' phony target rule
        n.newline(); n.build('stageassets', 'phony', [f'$stagedir/{final_asset}' for final_asset in final_assets], inputs_per_line=True)

        n.close()


    # Generate stage2 .ninja using ninja_syntax module
    with open(stage2_path, 'w') as output:
        n = ninja_syntax.Writer(output, 256)

        n.comment('SPDX-FileCopyrightText: 2026 Kevin Kahl')
        n.comment('SPDX-License-Identifier: Apache-2.0')

        n.comment(os.path.basename(stage2_path))

        n.newline(); n.comment('This file is generated and should not be edited directly.')
        n.newline(); n.comment('Build rules to satisfy the stage1 phony target inputs when $gendir != $stagedir (two stage builds).')

        for driver, final_driver in zip(drivers, final_drivers):
            if source_path:
                deps = [
                    f'$gendir/${{lib_prefix}}{dep}.groovy'
                    for dep in driver_deps[driver].inclib_names
                ]
                n.build(
                    f'$stagedir/{final_driver}',
                    'gpp_stage',
                    f'$gendir/{final_driver}',
                    implicit=deps,
                    inputs_per_line=True,
                )
            else:
                n.build(f'$stagedir/{final_driver}', 'gpp_stage', f'$gendir/{final_driver}')

        n.close()
