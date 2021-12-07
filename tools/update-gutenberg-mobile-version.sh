#!/bin/bash
############################################################
# Help                                                     #
############################################################
help()
{
   # Display Help
   echo "Updates the gutenberg mobile version"
   echo
   echo "Syntax: scriptTemplate [-h|t]"
   echo "options:"
   echo "h     Print this Help."
   echo "t     Guternberg Mobile tag"
   echo ""
   echo
}

get_gutenberg_from_PR () {
   echo "Update build.gradle file to Gutenberg Mobiler version $GUTENBERG_MOBILE_VERSION"
}
# Get the options
while getopts ":ht:" option; do
   case $option in
      h) # display Help
         help
         exit;;
      t) # Enter a GUTENBERG_MOBILE_VERSION
         GUTENBERG_MOBILE_VERSION=$OPTARG;;
        
     \?) # Invalid option
         echo "Error: Invalid option"
         exit;;
   esac
done
# Update gradle
echo "Update build.gradle file to Gutenberg Mobiler version $GUTENBERG_MOBILE_VERSION"
test -f "build.gradle"
sed -i'.orig' -E "s/ext.gutenbergMobileVersion = '(.*)'/ext.gutenbergMobileVersion = '${GUTENBERG_MOBILE_VERSION}'/" build.gradle
rm build.gradle.orig