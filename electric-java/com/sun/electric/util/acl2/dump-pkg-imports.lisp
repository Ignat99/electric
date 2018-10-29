(include-book "acl2s/portcullis" :dir :system)
(include-book "build/portcullis" :dir :system)
(include-book "centaur/aignet/portcullis" :dir :system)
(include-book "centaur/bed/portcullis" :dir :system)
(include-book "centaur/bitops/portcullis" :dir :system)
(include-book "centaur/bridge/portcullis" :dir :system)
(include-book "centaur/clex/portcullis" :dir :system)
(include-book "centaur/defrstobj/portcullis" :dir :system)
(include-book "centaur/depgraph/portcullis" :dir :system)
(include-book "centaur/fty/portcullis" :dir :system)
(include-book "centaur/getopt/portcullis" :dir :system)
(include-book "centaur/gl/portcullis" :dir :system)
(include-book "centaur/lispfloat/portcullis" :dir :system)
(include-book "centaur/memoize/portcullis" :dir :system)
(include-book "centaur/nrev/portcullis" :dir :system)
(include-book "centaur/satlink/portcullis" :dir :system)
(include-book "centaur/sv/portcullis" :dir :system)
(include-book "centaur/vl/portcullis" :dir :system)
(include-book "centaur/vl2014/portcullis" :dir :system)
(include-book "coi/adviser/portcullis" :dir :system)
(include-book "coi/alists/portcullis" :dir :system)
(include-book "coi/bags/portcullis" :dir :system)
(include-book "coi/defstructure/portcullis" :dir :system)
(include-book "coi/defung/portcullis" :dir :system)
(include-book "coi/dtrees/portcullis" :dir :system)
(include-book "coi/gacc/portcullis" :dir :system)
(include-book "coi/lists/portcullis" :dir :system)
(include-book "coi/maps/portcullis" :dir :system)
(include-book "coi/nary/portcullis" :dir :system)
(include-book "coi/paths/portcullis" :dir :system)
(include-book "coi/quantification/portcullis" :dir :system)
(include-book "coi/records/portcullis" :dir :system)
(include-book "coi/symbol-fns/portcullis" :dir :system)
(include-book "coi/util/portcullis" :dir :system)
(include-book "cowles/portcullis" :dir :system)
(include-book "data-structures/portcullis" :dir :system)
(include-book "data-structures/memories/portcullis" :dir :system)
(include-book "kestrel/soft/portcullis" :dir :system)
; (include-book "models/jvm/m1/m1" :dir :system) ; !! conflicts with projects/codewalker
(include-book "models/jvm/m5/m5" :dir :system) ; !!
(include-book "oslib/portcullis" :dir :system)
(include-book "projects/apply-model/portcullis" :dir :system)
(include-book "projects/codewalker/m1-version-3" :dir :system) ; !!
(include-book "projects/farray/portcullis" :dir :system)
; (include-book "projects/legacy-defrstobj/portcullis" :dir :system) ; conflicts with centaur/defrstobj
(include-book "projects/milawa/ACL2/portcullis" :dir :system)
(include-book "projects/paco/utilities" :dir :system)
(include-book "projects/sat/dimacs-reader/portcullis" :dir :system)
(include-book "projects/sat/lrat/portcullis" :dir :system)
(include-book "projects/sat/proof-checker-array/portcullis" :dir :system)
(include-book "projects/sat/proof-checker-itp13/portcullis" :dir :system)
(include-book "projects/sb-machine/portcullis" :dir :system)
; (include-book "projects/security/jfkr/jfkr" :dir :system) ; !! conflicts with models/jvm/m5
(include-book "projects/sidekick/portcullis" :dir :system)
(include-book "projects/taspi/proofs/sets" :dir :system) ; !!
(include-book "projects/x86isa/portcullis/portcullis" :dir :system)
(include-book "std/portcullis" :dir :system)
(include-book "system/doc/portcullis" :dir :system)
(include-book "tools/prettygoals/portcullis" :dir :system)
(include-book "rtl/rel11/portcullis" :dir :system)

(defconst *CORE_PACKAGES* '("COMMON-LISP" "ACL2" "ACL2-PC"))
(defconst *ACL2S_PACKAGES* '("DEFDATA" "CGEN" "ACL2S" "ACL2S B" "ACL2S BB" "ACL2S T"))
(defconst *BUILD_PACKAGES* '("BUILD"))
(defconst *CENTAUR_AIGNET_PACKAGES* '("AIGNET" "AIGNET$A" "AIGNET$C"))
(defconst *CENTAUR_BED_PACKAGES* '("BED"))
(defconst *CENTAUR_BITOPS_PACKAGES* '("BITOPS"))
(defconst *CENTAUR_BRIDGE_PACKAGES* '("BRIDGE"))
(defconst *CENTAUR_CLEX_PACKAGES* '("CLEX"))
(defconst *CENTAUR_DEFRSTOBJ_PACKAGES* '("RSTOBJ"))
(defconst *CENTAUR_DEPGRAPH_PACKAGES* '("DEPGRAPH"))
(defconst *CENTAUR_FTY_PACKAGES* '("FTY"))
(defconst *CENTAUR_GETOPT_PACKAGES* '("GETOPT" "GETOPT-DEMO"))
(defconst *CENTAUR_GL_PACKAGES* '("GL" "GL-SYM" "GL-THM" "GL-FACT" "GL-FLAG"))
(defconst *CENTAUR_LISPFLOAT_PACKAGES* '("LISPFLOAT"))
(defconst *CENTAUR_MEMOIZE_PACKAGES* '("MEMOIZE"))
(defconst *CENTAUR_NREV_PACKAGES* '("NREV"))
(defconst *CENTAUR_SATLINK_PACKAGES* '("SATLINK"))
(defconst *CENTAUR_SV_PACKAGES* '("SV"))
(defconst *CENTAUR_VL_PACKAGES* '("VL"))
(defconst *CENTAUR_VL2014_PACKAGES* '("VL2014"))
(defconst *COI_ADVISER_PACKAGES* '("ADVISER"))
(defconst *COI_ALISTS_PACKAGES* '("ALIST"))
(defconst *COI_BAGS_PACKAGES* '("BAG"))
(defconst *COI_DEFSTRUCTURE_PACKAGES* '("STRUCTURES"))
(defconst *COI_DEFUNG_PACKAGES* '("DEFUNG"))
(defconst *COI_DTREES_PACKAGES* '("DTREE"))
(defconst *COI_GACC_PACKAGES* '("GACC"))
(defconst *COI_LISTS_PACKAGES* '("LIST"))
(defconst *COI_MAPS_PACKAGES* '("MAP"))
(defconst *COI_NARY_PACKAGES* '("NARY"))
(defconst *COI_PATHS_PACKAGES* '("CPATH"))
(defconst *COI_QUANTIFICATION_PACKAGES* '("QUANT"))
(defconst *COI_RECORDS_PACKAGES* '("REC"))
(defconst *COI_SYMBOL-FNS_PACKAGES* '("SYMBOL-FNS"))
(defconst *COI_SYNTAX_PACKAGES* '("SYN"))
(defconst *COI_UTIL_PACKAGES* '("COI-DEBUG" "DEF" "DEFUN" "GENSYM" "RULE-SETS" "TABLE"))
(defconst *COWLES_PACKAGES* '("ACL2-CRG" "ACL2-AGP" "ACL2-ASG"))
(defconst *DATA-STRUCTURES_PACKAGES* '("U" "DEFSTRUCTURE"))
(defconst *DATA-STRUCTURES_MEMORIES_PACKAGES* '("MEM"))
(defconst *KESTREL_SOFT_PACKAGES* '("SOFT"))
(defconst *MODELS_JVM_M5_PACKAGES* '("LABEL" "JVM" "M5"))
(defconst *OSLIB_PACKAGES* '("OSLIB"))
(defconst *PROJECTS_APPLY-MODEL_PACKAGES* '("MODAPP"))
(defconst *PROJECTS_CODEWALKER_PACKAGES* '("M1"))
(defconst *PROJECTS_FARRAY_PACKAGES* '("FARRAY"))
;(defconst *PROJECTS_LEGACY_DEFRSTOBJ_PACKAGES* '("RSTOBJ"))
(defconst *PROJECTS_MILAWA_ACL2_PACKAGES* '("MILAWA"))
(defconst *PROJECTS_PACO_PACKAGES* '("PACO"))
(defconst *PROJECTS_SAT_DIMACS-READER_PACKAGES* '("DIMACS-READER"))
(defconst *PROJECTS_SAT_LRAT_PACKAGES* '("LRAT"))
(defconst *PROJECTS_SAT_PROOF-CHECKER-ARRAY_PACKAGES* '("PROOF-CHECKER-ARRAY"))
(defconst *PROJECTS_SAT_PROOF-CHECKER-ITP13_PACKAGES* '("PROOF-CHECKER-ITP13"))
(defconst *PROJECTS_SB-MACHINE_PACKAGES* '("SB"))
(defconst *PROJECTS_SECURITY_JFKR_PACKAGES* '("CRYPTO" "JFKR"))
(defconst *PROJECTS_SIDEKICK_PACKAGES* '("SIDEKICK"))
(defconst *PROJECTS_TASPI_PROOFS_PACKAGES* '("PROOF"))
(defconst *PROJECTS_X86ISA_PORTCULLIS_PACKAGES* '("X86ISA"))
(defconst *STD_PACKAGES* '("STR" "INSTANCE" "COMPUTED-HINTS" "SET" "XDOC"
                           "BITSETS" "STD" "STOBJS" "FLAG"))
(defconst *SYSTEM_DOC_PACKAGES* '("TOURS"))
(defconst *TOOLS_PRETTYGOALS_PACKAGES* '("PRETTYGOALS"))
(defconst *RTL_REL11_PACKAGES* '("RTL"))

(defconst *ALL_PACKAGES*
 (append
  *CORE_PACKAGES*
  *ACL2S_PACKAGES*
  *BUILD_PACKAGES*
  *CENTAUR_AIGNET_PACKAGES*
  *CENTAUR_BED_PACKAGES*
  *CENTAUR_BITOPS_PACKAGES*
  *CENTAUR_BRIDGE_PACKAGES*
  *CENTAUR_CLEX_PACKAGES*
  *CENTAUR_DEFRSTOBJ_PACKAGES*
  *CENTAUR_DEPGRAPH_PACKAGES*
  *CENTAUR_FTY_PACKAGES*
  *CENTAUR_GETOPT_PACKAGES*
  *CENTAUR_GL_PACKAGES*
  *CENTAUR_LISPFLOAT_PACKAGES*
  *CENTAUR_MEMOIZE_PACKAGES*
  *CENTAUR_NREV_PACKAGES*
  *CENTAUR_SATLINK_PACKAGES*
  *CENTAUR_SV_PACKAGES*
  *CENTAUR_VL_PACKAGES*
  *CENTAUR_VL2014_PACKAGES*
  *COI_ADVISER_PACKAGES*
  *COI_ALISTS_PACKAGES*
  *COI_BAGS_PACKAGES*
  *COI_DEFSTRUCTURE_PACKAGES*
  *COI_DEFUNG_PACKAGES*
  *COI_DTREES_PACKAGES*
  *COI_GACC_PACKAGES*
  *COI_LISTS_PACKAGES*
  *COI_MAPS_PACKAGES*
  *COI_NARY_PACKAGES*
  *COI_PATHS_PACKAGES*
  *COI_QUANTIFICATION_PACKAGES*
  *COI_RECORDS_PACKAGES*
  *COI_SYMBOL-FNS_PACKAGES*
  *COI_SYNTAX_PACKAGES*
  *COI_UTIL_PACKAGES*
  *COWLES_PACKAGES*
  *DATA-STRUCTURES_PACKAGES*
  *DATA-STRUCTURES_MEMORIES_PACKAGES*
  *KESTREL_SOFT_PACKAGES*
  *MODELS_JVM_M5_PACKAGES*
  *OSLIB_PACKAGES*
  *PROJECTS_APPLY-MODEL_PACKAGES*
  *PROJECTS_CODEWALKER_PACKAGES*
  *PROJECTS_FARRAY_PACKAGES*
;  *PROJECTS_LEGACY_DEFRSTOBJ_PACKAGES*
  *PROJECTS_MILAWA_ACL2_PACKAGES*
  *PROJECTS_PACO_PACKAGES*
  *PROJECTS_SAT_DIMACS-READER_PACKAGES*
  *PROJECTS_SAT_LRAT_PACKAGES*
  *PROJECTS_SAT_PROOF-CHECKER-ARRAY_PACKAGES*
  *PROJECTS_SAT_PROOF-CHECKER-ITP13_PACKAGES*
  *PROJECTS_SB-MACHINE_PACKAGES*
;  *PROJECTS_SECURITY_JFKR_PACKAGES*
  *PROJECTS_SIDEKICK_PACKAGES*
  *PROJECTS_TASPI_PROOFS_PACKAGES*
  *PROJECTS_X86ISA_PORTCULLIS_PACKAGES*
  *STD_PACKAGES*
  *SYSTEM_DOC_PACKAGES*
  *TOOLS_PRETTYGOALS_PACKAGES*
  *RTL_REL11_PACKAGES*))

(defun expand-symbol-list (slist)
  (if (atom slist)
      ()
    (cons
     (cons (symbol-package-name (car slist))
           (symbol-name (car slist)))
     (expand-symbol-list (cdr slist)))))

(defun collect-pkg-imports (pkglist)
  (if (atom pkglist)
      ()
    (cons (cons (car pkglist)
                (expand-symbol-list (pkg-imports (car pkglist))))
          (collect-pkg-imports (cdr pkglist)))))

(defun collect-empty-pkg-imports (pkglist acc)
  (if (atom pkglist)
      acc
    (let ((imports (pkg-imports (car pkglist))))
      (collect-empty-pkg-imports
       (cdr pkglist)
       (if imports acc (cons (car pkglist) acc))))))


(collect-empty-pkg-imports *ALL_PACKAGES* ())

(serialize-write
 "pkg-imports.sao"
  (collect-pkg-imports *ALL_PACKAGES*))
