package com.shorty.auth;

import com.shorty.auth.ApiKeyService.CreateKeyRequest;
import com.shorty.auth.ApiKeyService.CreatedKey;
import com.shorty.auth.ApiKeyService.MaskedKey;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService keys;

    public ApiKeyController(ApiKeyService keys) {
        this.keys = keys;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedKey create(@RequestBody(required = false) CreateKeyRequest body) {
        AuthPrincipal user = SecuritySupport.requireUser();
        if (!user.canWrite()) {
            throw new com.shorty.api.ApiException(
                    HttpStatus.FORBIDDEN, "forbidden", "write scope required");
        }
        return keys.create(user.userId(), body == null ? null : body.scopes());
    }

    @GetMapping
    public List<MaskedKey> list() {
        return keys.list(SecuritySupport.requireUser().userId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable long id) {
        AuthPrincipal user = SecuritySupport.requireUser();
        if (!user.canWrite()) {
            throw new com.shorty.api.ApiException(
                    HttpStatus.FORBIDDEN, "forbidden", "write scope required");
        }
        keys.revoke(user.userId(), id);
    }
}
