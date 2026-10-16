const { withAppBuildGradle } = require('expo/config-plugins');

// 앱 모듈의 C++ 빌드에 쓸 CMake 버전을 지정한다. 지정하지 않으면 AGP 기본값(3.22.1)을 쓴다.
// 3.22.1에 든 ninja는 Windows에서 260자를 넘는 경로를 다루지 못해, 신규 아키텍처 코드 생성 결과(.o) 경로에서 빌드가 실패한다.
// 3.31의 ninja(1.12)는 Windows의 LongPathsEnabled가 켜져 있으면 긴 경로를 다룬다.
const CMAKE_VERSION = '3.31.6';

module.exports = (config) =>
  withAppBuildGradle(config, (config) => {
    if (!config.modResults.contents.includes('externalNativeBuild { cmake { version'))
      config.modResults.contents = config.modResults.contents.replace(
        /^android \{\n/m,
        `android {\n    externalNativeBuild { cmake { version "${CMAKE_VERSION}" } }\n`
      );
    return config;
  });
