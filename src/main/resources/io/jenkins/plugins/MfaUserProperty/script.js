// /src/main/resources/io/jenkins/plugins/MfaUserProperty/script.js
document.addEventListener('DOMContentLoaded', function () {
    initMfaToggle();
});

function getCsrfHeaders() {
    var crumbField = document.querySelector('input[name=".crumb"], input[name="Jenkins-Crumb"]');
    if (!crumbField) return {};
    return { 'Jenkins-Crumb': crumbField.value };
}

function initMfaToggle() {
    var checkbox = document.getElementById('mfaEnabledCheckbox');
    var container = document.getElementById('mfaSetupContainer');
    var qrImage = document.getElementById('qrCodeImage');
    var secretInput = document.getElementById('mfaSecretKey');
    var backupSection = document.getElementById('backupCodesSection');
    var backupList = document.getElementById('backupCodesList');

    if (!checkbox || !container) return;

    function showOrHideMfaSetup() {
        if (checkbox.checked) {
            container.classList.remove('hidden');
            container.classList.add('visible');

            if (!qrImage.src || qrImage.src === window.location.href) {
                fetch(window.rootURL + '/mfa-totp/generateSecret', {
                    method: 'GET',
                    headers: Object.assign({ 'Accept': 'application/json' }, getCsrfHeaders()),
                    credentials: 'same-origin'
                })
                    .then(function (resp) {
                        if (!resp.ok) throw new Error('HTTP ' + resp.status);
                        return resp.json();
                    })
                    .then(function (data) {
                        qrImage.src = window.rootURL + '/mfa-totp/qrcode?secret=' +
                            encodeURIComponent(data.secret);
                        secretInput.value = data.secret;

                        if (data.backupCodes && data.backupCodes.length > 0) {
                            backupList.innerHTML = '';
                            data.backupCodes.forEach(function (code) {
                                var span = document.createElement('span');
                                span.className = 'backup-code';
                                span.textContent = code;
                                backupList.appendChild(span);
                            });
                            backupSection.classList.remove('hidden');
                            backupSection.classList.add('visible');
                        }
                    })
                    .catch(function (err) {
                        console.error('Error generating MFA secret:', err);
                        alert('Failed to generate MFA secret. Please refresh and try again.');
                    });
            }
        } else {
            container.classList.remove('visible');
            container.classList.add('hidden');
            if (backupSection) {
                backupSection.classList.remove('visible');
                backupSection.classList.add('hidden');
            }
            qrImage.src = '';
            secretInput.value = '';
        }
    }

    checkbox.addEventListener('change', showOrHideMfaSetup);

    if (checkbox.checked) {
        showOrHideMfaSetup();
    }
}
