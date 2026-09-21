warning: in the working copy of '.env.example', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/build.gradle.kts', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/FincoreApplication.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/ingestion/api/ImportController.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/ingestion/application/ImportBatchTransactionalSteps.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/ingestion/application/ImportFileUseCase.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/ingestion/domain/ImportBatch.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/ingestion/infrastructure/ImportBatchRepository.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/ingestion/infrastructure/IngestionProperties.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/platform/web/GlobalErrorHandler.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/java/dev/fincore/shared/error/ErrorCode.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/resources/application-dev.yml', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/resources/application-local.yml', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/resources/application-test.yml', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/main/resources/application.yml', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/test/java/dev/fincore/architecture/ArchitectureRules.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/test/java/dev/fincore/ingestion/api/ImportControllerIntegrationTest.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/test/java/dev/fincore/ingestion/application/ImportFileUseCaseIntegrationTest.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/test/java/dev/fincore/ingestion/application/ImportFileUseCaseIntegrityScanIntegrationTest.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'backend/src/test/java/dev/fincore/ingestion/domain/ImportBatchTest.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'docker-compose.yml', LF will be replaced by CRLF the next time Git touches it
 .env.example                                       |  13 [32m+[m[31m-[m
 backend/build.gradle.kts                           |   8 [32m++[m
 .../main/java/dev/fincore/FincoreApplication.java  |   8 [32m+[m[31m-[m
 .../fincore/ingestion/api/ImportController.java    |  31 [32m++++[m[31m-[m
 .../application/ImportBatchTransactionalSteps.java |  29 [32m+++[m[31m-[m
 .../ingestion/application/ImportFileUseCase.java   | 150 [32m+++[m[31m------------------[m
 .../dev/fincore/ingestion/domain/ImportBatch.java  |  22 [32m++[m[31m-[m
 .../infrastructure/ImportBatchRepository.java      |  18 [32m+++[m
 .../infrastructure/IngestionProperties.java        |  10 [32m+[m[31m-[m
 .../fincore/platform/web/GlobalErrorHandler.java   |  11 [32m++[m
 .../java/dev/fincore/shared/error/ErrorCode.java   |   3 [32m+[m
 backend/src/main/resources/application-dev.yml     |   5 [32m+[m
 backend/src/main/resources/application-local.yml   |   6 [32m+[m
 backend/src/main/resources/application-test.yml    |   6 [32m+[m
 backend/src/main/resources/application.yml         |  40 [32m++++++[m
 .../fincore/architecture/ArchitectureRules.java    |   8 [32m+[m[31m-[m
 .../api/ImportControllerIntegrationTest.java       |  98 [32m+++++++++++++[m[31m-[m
 .../ImportFileUseCaseIntegrationTest.java          |  18 [32m++[m[31m-[m
 ...ortFileUseCaseIntegrityScanIntegrationTest.java |  11 [32m+[m[31m-[m
 .../fincore/ingestion/domain/ImportBatchTest.java  |  40 [32m+++++[m[31m-[m
 docker-compose.yml                                 |  21 [32m++[m[31m-[m
 21 files changed, 396 insertions(+), 160 deletions(-)
