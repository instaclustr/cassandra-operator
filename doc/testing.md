## Testing

Tests are in `test` directory. They are based on Operator SDK testing framework.

They are executed like:

```
operator-sdk test local ./test/e2e \
    --up-local \
    --namespace default \
    --go-test-flags "-timeout=20m" \
    --debug --verbose 
```
