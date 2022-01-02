#!/usr/local/bin/bash
function show_help() {
cat >&2 << EOF
   Usage: ${0##*/} [-p PACKAGE] [-c COUNTRY] [-u USER]
   PACKAGE is Extended or Professional_6 or Professional_18 or Professional_30 or History or Budget or Ocr
   Generate invoice 
EOF
exit 1
}

while getopts "p:c:u:" opt; do
    case "$opt" in
        p) case "$OPTARG" in
               History)
                 export KEY="Budgeting"
                 export PRICE=4.3
                 ;;
               Budget)
                 export KEY="Budgeting"
                 export PRICE=4.3
                 ;;
               Ocr)
                 export KEY="Scan receipt"
                 export PRICE=4.3
                 ;;
               Extended)
                 export KEY="My Expenses Extended Licence"
                 export PRICE=6.7
                 ;;
               Professional_6)
                 export KEY="My Expenses Professional Licence 6 months"
                 export PRICE=5
                 ;;
               Professional_12)
                 export KEY="My Expenses Professional Licence 1 year"
                 export PRICE=8
                 ;;
               Professional_24)
                 export KEY="My Expenses Professional Licence 2 years"
                 export PRICE=15
                 ;;
            esac
           ;;
        c) export COUNTRY=$OPTARG
           ;;
        u) export KUNDE=$OPTARG
           ;;
        '?')
            show_help
            ;;
    esac
done

if [ -z "$KEY" ] || [ -z "$PRICE" ] || [ -z "$COUNTRY" ] || [ -z "$KUNDE" ]
  then
     show_help
fi

: "${TEMPLATE:=Invoice.tmpl}"

(
cd /Users/michaeltotschnig/Documents/MyExpenses.business/invoices
YEAR=$(date +'%Y')
MONTH=$(date +'%m')
if test -f LATEST
  then
    LATEST=$(<LATEST)
    arrLATEST=(${LATEST//-/ })
    LATEST_MONTH=${arrLATEST[0]}
    LATEST_NUMBER=${arrLATEST[1]}

    if [ "$MONTH" == "${LATEST_MONTH}" ]
      then
        let LATEST_NUMBER+=1
      else
        LATEST_NUMBER=1
    fi
  else
    LATEST_NUMBER=1
fi

export NUMBER=${YEAR}-${MONTH}-${LATEST_NUMBER}

FILENAME=Invoice-${NUMBER}
TEXFILE=${FILENAME}.tex
if test -f "$TEXFILE"; then
    echo "$TEXFILE exists."
    exit 1
fi
envsubst < $TEMPLATE > $TEXFILE
pdflatex $TEXFILE
echo ${MONTH}-${LATEST_NUMBER} >LATEST
open ${FILENAME}.pdf
)
