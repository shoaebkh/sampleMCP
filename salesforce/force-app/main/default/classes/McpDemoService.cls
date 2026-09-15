/**
 * Minimal Apex REST service exposed to the sample MCP server via the
 * salesforce_get_account and salesforce_create_case tools.
 *
 * Runs in the security context of whichever user the JWT Bearer flow
 * authenticates as, so that user's sharing rules, object and field-level
 * security still apply (this class uses "with sharing").
 */
@RestResource(urlMapping='/mcp-demo/*')
global with sharing class McpDemoService {

    global class AccountResult {
        global Id id;
        global String name;
        global String industry;
        global String phone;
    }

    /**
     * GET /services/apexrest/mcp-demo/?name=Acme
     * Looks up a single Account by exact name. Returns 404 if not found,
     * 400 if the name parameter is missing.
     */
    @HttpGet
    global static AccountResult getAccount() {
        String accountName = RestContext.request.params.get('name');
        if (String.isBlank(accountName)) {
            RestContext.response.statusCode = 400;
            return null;
        }

        List<Account> accounts = [
            SELECT Id, Name, Industry, Phone
            FROM Account
            WHERE Name = :accountName
            LIMIT 1
        ];

        if (accounts.isEmpty()) {
            RestContext.response.statusCode = 404;
            return null;
        }

        AccountResult result = new AccountResult();
        result.id = accounts[0].Id;
        result.name = accounts[0].Name;
        result.industry = accounts[0].Industry;
        result.phone = accounts[0].Phone;
        return result;
    }

    /**
     * POST /services/apexrest/mcp-demo/
     * Body: {"subject": "...", "description": "...", "priority": "Medium"}
     * Creates a Case and returns its Id. Apex REST automatically binds the
     * JSON body fields to these method parameters by name.
     */
    @HttpPost
    global static String createCase(String subject, String description, String priority) {
        if (String.isBlank(subject)) {
            RestContext.response.statusCode = 400;
            return 'subject is required';
        }

        Case newCase = new Case(
            Subject = subject,
            Description = description,
            Priority = String.isBlank(priority) ? 'Medium' : priority
        );
        insert newCase;
        return newCase.Id;
    }
}
