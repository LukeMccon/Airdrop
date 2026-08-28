#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
lightkeeper_commit="be585af08221c37bcbc8c9d7f5a40a27dbd2dff1"
source_sha256="defff158d56b215c9756e0042dc456fc09b76e1f13cfc23d6a3941aefac444e7"
adapter_group="com.airdropmc.lightkeeper-adapter"
adapter_version="${lightkeeper_commit}-airdrop1"
adapter_repository="$script_dir/target/lightkeeper-plugin-repository"
artifact_directory="$adapter_repository/com/airdropmc/lightkeeper-adapter/lightkeeper-maven-plugin/$adapter_version"
adapter_jar="$artifact_directory/lightkeeper-maven-plugin-$adapter_version.jar"
adapter_pom="$artifact_directory/lightkeeper-maven-plugin-$adapter_version.pom"
source_url="https://jitpack.io/com/github/PimvanderLoos/LightKeeper/lightkeeper-maven-plugin/$lightkeeper_commit/lightkeeper-maven-plugin-$lightkeeper_commit.jar"

temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT HUP INT TERM
source_jar="$temporary_directory/lightkeeper-maven-plugin.jar"
extracted_directory="$temporary_directory/extracted"

curl -fsSL "$source_url" -o "$source_jar"
actual_sha256=$(sha256sum "$source_jar" | awk '{print $1}')
if [ "$actual_sha256" != "$source_sha256" ]; then
	printf 'LightKeeper plugin checksum mismatch: expected %s, got %s\n' "$source_sha256" "$actual_sha256" >&2
	exit 1
fi

mkdir -p "$extracted_directory" "$artifact_directory"
unzip -q "$source_jar" META-INF/maven/plugin.xml -d "$extracted_directory"
plugin_descriptor="$extracted_directory/META-INF/maven/plugin.xml"

sed -i.bak \
	-e 's|<groupId>nl.pim16aap2.lightkeeper</groupId>|<groupId>com.github.PimvanderLoos.LightKeeper</groupId>|g' \
	-e "s|<version>1.2.0-SNAPSHOT</version>|<version>$lightkeeper_commit</version>|g" \
	"$plugin_descriptor"
sed -i.adapter \
	-e "1,/<groupId>com.github.PimvanderLoos.LightKeeper<\/groupId>/ s|<groupId>com.github.PimvanderLoos.LightKeeper</groupId>|<groupId>$adapter_group</groupId>|" \
	-e "1,/<version>$lightkeeper_commit<\/version>/ s|<version>$lightkeeper_commit</version>|<version>$adapter_version</version>|" \
	"$plugin_descriptor"
rm -f "$plugin_descriptor.bak" "$plugin_descriptor.adapter"

cp "$source_jar" "$adapter_jar"
jar --update --file "$adapter_jar" -C "$extracted_directory" META-INF/maven/plugin.xml
cp "$script_dir/lightkeeper-maven-plugin-adapter.pom.xml" "$adapter_pom"
for artifact in "$adapter_jar" "$adapter_pom"; do
	sha1sum "$artifact" | awk '{print $1}' > "$artifact.sha1"
	sha256sum "$artifact" | awk '{print $1}' > "$artifact.sha256"
done

unzip -p "$adapter_jar" META-INF/maven/plugin.xml | grep -q "<groupId>$adapter_group</groupId>"
unzip -p "$adapter_jar" META-INF/maven/plugin.xml | grep -q "<version>$adapter_version</version>"
printf 'Prepared pinned LightKeeper Maven plugin adapter at %s\n' "$adapter_jar"
